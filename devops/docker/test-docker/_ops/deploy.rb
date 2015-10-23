###################
# ECS Deploy code #
###################

ACTION_TAG = `echo "$(whoami)_$(date +%s)"`.strip()

# Tag definition
def tag(repo, service, env)
    "#{repo}:#{service}_#{env}_#{ACTION_TAG}"
end


def _sys(cmd)
  # "set -e" style system call
  system(cmd) or exit 1
end


# Find the image id constructed by a "docker compose build"
def get_images(build_services)
    `docker images`.split(/\r?\n/)[1..-1].map { |i|
        # Generate rows
        i.split(/\s+/)
    }.select { |row|
        # Filter images to just those in build_services
        build_services.include?(row[0].split("_")[-1])
    }.inject({}) { |hsh, image|
        # Return service -> image hash
        hsh[image[0].split("_")[-1]] = image[2]; hsh
    }
end


# Convert docker-compose.yml to ECS JSON task definitions
def convert_DCYML(ymlpath)
    JSON.parse `cat #{ymlpath} | container-transform -v 2> /dev/null`
end


def inject_env(container, env_vars)
    container["environment"] ||= []
    container["environment"] += env_vars.map { | env |  
        {"name" => env[0], "value" => env[1]}
    }
end


def inject_default_constraints(container)
    container["cpu"] = (container["cpu"] or 10)
    container["memory"] = (container["memory"] or 256)
end


# Download secrets from S3 to tmp dir, load, and remove
def get_cluster_secrets(env)
    _sys("mkdir -p tmp/")
    secret_conf_path = "tmp/secrets.yml"
    _sys("aws s3 cp #{DEPLOY_CONF[env][:secret_conf_uri]} #{secret_conf_path}")
    secrets = YAML.load_file(secret_conf_path)
    _sys("rm #{secret_conf_path}")
    return secrets
end


# Convenience wrapper to call aws cli in a structured way
def aws_ecs(cmd, options)
    opt_str = options.map { |k, v| "--#{k} #{v}" }.join " "
    resp = `aws ecs #{cmd} #{opt_str}`
    begin
        return JSON.parse resp
    rescue JSON::ParserError
    end
end


def update_service(service, cluster, task, revision)
    # Get the original desired task count
    desired_count = aws_ecs(
        "describe-services",
        {"cluster" => cluster, "services" => service}
    )["services"][0]["desiredCount"]
    
    # Update the task definition for the service at a reduced count
    aws_ecs(
        "update-service",
        {
            "cluster" => cluster,
            "service" => service,
            "task-definition" => "#{task}:#{revision}",
            "desired-count" => [desired_count - 1, 1].max
        }
    )

    # Wait for the deployment to finish
    begin 
        puts "Waiting for #{service} deploy to complete..."
        sleep 10
    end until (
        aws_ecs(
            "describe-services",
            {"cluster" => cluster, "services" => service}
        )["services"][0]["deployments"].select { |dep|
            dep["status"] == "ACTIVE"
        }.length == 0
    )

    # Restore the desired count
    puts "Restoring the desired container count"
    aws_ecs(
        "update-service",
        {
            "cluster" => cluster,
            "service" => service,
            "desired-count" => desired_count
        }
    )
    puts "Finished pushing #{service}".green
end


class String
    # For pretty formatting
    def red; "\033[31m#{self}\033[0m" end
    def green; "\033[32m#{self}\033[0m" end
    def white; "\033[1;37m#{self}\033[0m" end
end


DEPLOY_CONF = {
    "test" => {
        :cluster => "demandmgt-test",
        :secret_conf_uri => "s3://deployment-configs/demandmgt-secret-conf-test.yml",
        :dockerhub_repo => "enernoclabs/demandmgt",
        :env_tag => "test"
    }
}


def push_to_dockerhub(env)
    repo = DEPLOY_CONF[env][:dockerhub_repo]
    build_services = PRD_DCY.keys.select { |k| !PRD_DCY[k]['build'].nil? }

    image_ids = get_images(build_services)
    if(image_ids.keys.eql? build_services)
        puts "Not all images were built."
        exit 1
    end

    tags = {}
    build_services.each do | service |
        tag = tag(repo, service, env)

        puts "Tagging image #{image_ids[service]} with tag #{tag}".white
        _sys("docker tag #{image_ids[service]} #{tag}")

        puts "Pushing image to dockerhub repo #{repo}".white
        _sys("docker push #{tag}")

        # Track the new dockerhub tag
        tags[service] = tag
    end

    return tags
end

def update_ecs(env, tags)
    puts "Converting docker-compose-prd.yml to ECS task definition"
    ecsJSON = convert_DCYML('docker-compose-prd.yml')
    secrets = get_cluster_secrets(env)

    # Get deploy secrets from S3
    # Deploy each service
    ecsJSON["containerDefinitions"].each do | container |
        # Update the container image to point to the one we just built
        service = container["name"]
        container["image"] = tags[service]

        # Format service and env for ECS names
        service_name = "demandmgt-#{service}-service-#{DEPLOY_CONF[env][:env_tag]}"
        task_name = "demandmgt-#{service}-task-#{DEPLOY_CONF[env][:env_tag]}"

        # Insert secrets into the container definitions
        secrets[container["name"]]["NODE_ENV"] = env
        inject_env(container, (secrets[service] || {}))

        # Use default memory/CPU constraints for now
        inject_default_constraints(container)

        puts "Registering task definition #{task_name}...".white
        containerdef = [container]
        task_revision = aws_ecs(
            "register-task-definition",
            {
                "family" => task_name,
                "container-definitions" => "'#{containerdef.to_json}'",
                "volumes" => "'#{ecsJSON['volumes'].to_json}'"
            }
        )["taskDefinition"]["revision"]

        puts "Updating service #{service_name} to use task revision " \
             "#{task_revision}".white
        update_service(
            service_name,
            DEPLOY_CONF[env][:cluster],
            task_name,
            task_revision
        )
    end
end


def deploy(env)
    # Sanity check production pushing
    if env == "production"
        if `git rev-parse --abbrev-ref HEAD` != "master"
            raise "Refuse to push non-master branch to production".red
        end
    end
    if `git status --porcelain`.length > 0
        raise "Refuse to push dirty branch"
    end
    puts "Building ALC images".white
    Rake::Task["build"].execute
    update_ecs(env, push_to_dockerhub(env))
    puts "All done!".green
end

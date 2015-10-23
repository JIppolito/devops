# Automated Load Control
External service to allow manual or automatic curtailments outside of DR events.

## Setup
Development should take place within a docker containers running inside a
local VM. To begin, make sure you have the following installed:

- docker
- docker-machine
- docker-compose
- VirtualBox

[Download and install Virtualbox](https://www.virtualbox.org/wiki/Downloads)

You can install the docker tools using brew.

```bash
$ brew update
$ brew install docker docker-compose docker-machine
```

You should have the following versions (or later):

- docker: 1.6.0
- docker-machine: 0.2.0
- docker-compose: 1.2.0
- boot2docker: 1.5.0

Once everything is installed, run the following command to create a docker
machine for this project:

```bash
# Make sure you're logged into dockerhub with your Enernoclabs account
$ docker login

# Spin up the boot2docker VM
$ docker-machine create -d virtualbox demandmgt

# Run the machine setup
$ rake setupVM
```

Next, make sure your environment variables describing your docker host are set:

```bash
$ eval "$(docker-machine env demandmgt)"
```

Now you can docker-compose to spin up the app in a container inside the VM you
just created:

```bash
$ docker-compose up
```

## Working with the Dev Env

Make your life much easier and add the following to your `.zshrc` (or similar):

```bash
alias dm='docker-machine'
alias dc='docker-compose'
```

Running tests can be done through a rake task

```bash
# Run tests for all layers
$ rake test

# Run tests for a single layer
$ rake lcbs:test
```

Running scripts is done through calls to `grunt` through docker-compose:

```bash
# Seed the PG db with sample data
$ dc run lcbs grunt:seedDB

# Run a migration
$ dc run lcbs grunt:migrate --name 001-add-user-dates --op up

# Generate a migration (creates a migration file)
$ dc run lcbs grunt:migrate --name 017-foo-bar-baz --op generate

# Recreate the test database
$ dc run lcbs grunt:createTestDB
```

Note that if you change either the `package.json` or `Gruntfile.js`, you will
need to rebuild the base container for your changes to take effect:

```bash
$ docker-compose build && docker-compose up
```
## Deploying

Setup and install dependencies.  You'll need to install
[pip](https://pip.pypa.io/en/latest/installing.html) and
[homebrew](http://brew.sh/).

```bash
# Install the AWS command line interface
$ brew update
$ brew install awscli

# Configure your AWS command line interface
$ aws configure

# Install the container-transform gem
$ sudo pip install container-transform
```

Then you can push to a cluster, although the script checks that your branch is
at least committed before performing the build and push:

```bash
# Push to staging
$ rake staging:deploy
```

## Modifying the Base ECS Image
The ECS cluster uses an autoscale group to create new host EC2 images.  The
splunk forwarder agent runs on the host, so we need to create a base AMI for the
auto-scale group to launch with the splunk forwarder installed

```bash
# Set the following environment variables:
$ SPLUNK_USER=admin
$ SPLUNK_PASS=changeme
$ AWS_ACCESS_KEY=AKAIYOURACCESSKEY
$ AWS_SECRET_KEY=FOOBARBAZZHANDS

# Run the packer script to create a new AMI
$ packer build _ops/packer/ecs_base.json
```

Then update the launch configuration used by the demandmgt auto-scale group to the
newly created image.


## Troubleshooting

#### Can't connect to the docker-machine?
`docker` and `docker-compose` use the network addresses set in some environment
variables for the docker machine.  Try `eval "$(docker-machine env demandmgt)"` to
reset those variables.

#### "Client and server don't have same version" error
Upgrade your docker machine (with your `demandmgt` machine running), do
`docker-machine upgrade demandmgt`.

#### I interrupted a deploy, and now docker won't push to dockerhub
Try restarting your docker-machine.

#### "Waiting for VM to start..." doesn't complete and demandmgt has no url
Check that your hosts file (`/etc/hosts`) contains the following:
`127.0.0.1 localhost`

Use boot2docker version 1.5.0 (downgrading if necessary)

Remove any existing `demandmgt` or `boot2docker` VM via Virtualbox
Remove any 'host-only networks' in Virtualbox -- `Preferences > Network > Host-only Networks

Setup boot2docker, with verbose mode to see any errors:

```bash
$ boot2docker -v init
$ boot2docker -v up
```

If these executing successfully, docker-machine should be able to create `demandmgt`

require 'aws-sdk'

#############
# AWS Utils #
#############
class AWSUtils
  attr_accessor :if_tag_empty, :stop_time

  def initialize(region, aws_id, aws_sid, stop_date = 7,
      size_of = 1024, if_tag_empty = true)
    @ec2 = Aws::EC2::Client.new(
        region: region,
        access_key_id: aws_id,
        secret_access_key: aws_sid
    )

    @autoscaling = Aws::AutoScaling::Client.new(
        region: region,
        access_key_id: aws_id,
        secret_access_key: aws_sid
    )

    # Limit the size of the volume to search
    @size_of = size_of

    # Older than 7 days GMT
    @stop_time = (Time.now.utc - (60 * 60 * 24 * stop_date))

    @if_tag_empty = if_tag_empty
  end

  ##################
  # SNAPSHOT UTILS #
  ##################

  def all_snapshots
    results = []

    resp = @ec2.describe_snapshots(owner_ids: ['self'])
    more_results = true

    while more_results
      resp.snapshots.each do |snapshot|
        results << snapshot
      end

      if resp.next_token.nil?
        more_results = false
      else
        resp = @ec2.describe_snapshots(
          next_token: resp.next_token,
          owner_ids: ['self']
        )
      end
    end

    results
  end

  def filter_snapshots_one_week(snapshots)
    results = []
    snapshots.each do |snapshot|
      if snapshot.volume_size <= @size_of && snapshot.start_time <= @stop_time
        if @if_tag_empty && snapshot.tags.empty?
          results << snapshot
        elsif ! @if_tag_empty
          results << snapshot
        end
      end
    end
    results
  end

  ##
  # Retreives the volumes associated with each snapshot provided
  #
  # snapshots - list of snapshot objects
  #
  # Returns a Hash where key = snapshot_id and value = array of volumes
  def volumes_by_snapshot(snapshots)
    results = {}
    snapshots.each do |snapshot|
      results[snapshot.snapshot_id] = []
    end

    resp = @ec2.describe_volumes(
      filters: [
        {
          name: 'snapshot-id',
          values: results.keys
        }
      ]
    )

    more_results = true

    while more_results
      resp.volumes.each do |volume|
        results[volume.snapshot_id] << volume unless volume.snapshot_id.empty?
      end

      if resp.next_token.nil?
        more_results = false
      else
        resp = @ec2.describe_volumes(
          filters: [
            {
              name: 'snapshot-id',
              values: results.keys
            }
          ],
          next_token: resp.next_token
        )
      end

    end
    results
  end

  ##
  # Retreives the AMIs associated with each snapshot provided
  #
  # snapshots - list of snapshot objects
  #
  # Returns a Hash where key = snapshot_id and value = array of AMIs
  def amis_by_snapshot(snapshots)
    results = {}
    snapshots.each do |snapshot|
      results[snapshot.snapshot_id] = []
    end

    @ec2.describe_images(
      owners: ['self'],
      filters: [
        {
          name: 'block-device-mapping.snapshot-id',
          values: results.keys
        }
      ]
    ).images.each do |ami|
      ami.block_device_mappings.each do |bdm|
        results[bdm.ebs.snapshot_id] << ami unless bdm.ebs.nil?
      end
    end

    results
  end

  ##################
  # VOLUME UTILS #
  ##################

  def all_volumes
    results = []

    resp = @ec2.describe_volumes

    more_results = true

    while more_results
      resp.volumes.each do |volume|
        results << volume
      end

      if resp.next_token.nil?
        more_results = false
      else
        resp = @ec2.describe_volumes(
          next_token: resp.next_token
        )
      end
    end

    results
  end

  ##
  # Retreives the instances associated with each volume provided
  #
  # volumes - list of volume objects
  #
  # Returns a Hash where key = volume_id and value = array of instances
  def instances_by_volume(volumes)
    results = {}
    volumes.each do |volume|
      results[volume.volume_id] = []
    end

    resp = @ec2.describe_instances(
      filters: [
        {
          name: 'block-device-mapping.volume-id',
          values: results.keys
        }
      ]
    )

    more_results = true

    while more_results

      resp.reservations.each do |reservation|
        reservation.instances.each do |instance|
          instance.block_device_mappings.each do |bdm|
            unless bdm.ebs.nil?
              if results.key?(bdm.ebs.volume_id)
                results[bdm.ebs.volume_id] << instance
              end
            end
          end
        end
      end

      if resp.next_token.nil?
        more_results = false
      else
        resp = @ec2.describe_instances(
          filters: [
            {
              name: 'block-device-mapping.volume-id',
              values: results.keys
            }
          ],
          next_token: resp.next_token
        )
      end

    end

    results
  end

  ##################
  # Launch Configs #
  ##################

  def all_launch_configs
    launch_configs = []
    resp = @autoscaling.describe_launch_configurations

    loop do
      resp[:launch_configurations].each do |launch_config|
        launch_configs << launch_config
      end

      break if resp.next_token.nil?

      resp = @autoscaling.describe_launch_configurations(
        next_token: resp.next_token
      )
    end

    launch_configs
  end

  ##
  # Retreives Autoscale groups sorted by their launch_configuration
  #
  # Returns a Hash where key = launch_configuraion_name & value = array of ASGs
  def asgs_by_launch_config
    results = {}
    resp = @autoscaling.describe_auto_scaling_groups
    more_results = true

    while more_results

      resp.auto_scaling_groups.each do |asg|
        if results.key?(asg.launch_configuration_name)
          results[asg.launch_configuration_name] << asg
        else
          results[asg.launch_configuration_name] = [asg]
        end
      end

      if resp.next_token.nil?
        more_results = false
      else
        resp = @autoscaling.describe_auto_scaling_groups(
          next_token: resp.next_token
        )
      end

    end
    results
  end

  #############
  # AMI Utils #
  #############

  def all_amis
    amis = []
    resp = @ec2.describe_images(owners: ['self'])
    amis = resp.images unless resp.images.empty?
    amis
  end

  ##
  # Retreives the instances associated with each ami provided
  #
  # amis - list of AMI objects
  #
  # Returns a Hash where key = ami_id and value = array of instances
  def instances_by_ami(amis)
    results = {}
    amis.each do |ami|
      results[ami.image_id] = []
    end

    @ec2.describe_instances(
      filters: [
        {
          name: 'image-id',
          values: results.keys
        }
      ]
    )[0].each do |reservation|
      reservation.instances.each do |instance|
        results[instance.image_id] << instance
      end
    end

    results
  end

  ##
  # Retreives the launch configuration for each ami provided
  #
  # amis - list of AMI objects
  #
  # Returns a Hash where key = ami-id and value = an array of launch-configs
  def launch_configs_by_ami(amis)
    results = {}
    amis.each do |ami|
      results[ami.image_id] = []
    end

    resp = @autoscaling.describe_launch_configurations

    more_results = true

    while more_results

      resp.launch_configurations.each do |launch_config|
        if results.keys.include?(launch_config.image_id)
          results[launch_config.image_id] << launch_config
        end
      end

      if resp.next_token.nil?
        more_results = false
      else
        resp = @autoscaling.describe_launch_configurations(
          next_token: resp.next_token
        )
      end

    end

    results
  end

  #############
  # Logging
  #############

  def pretty_print_ami(ami)
    pretty_string = "AMI ID: #{ami.image_id} #{ami.name} #{ami.creation_date}"\
      " #{ami.tags}"
    puts pretty_string
  end

  def pretty_print_asg(asg)
    pretty_string = "ASG Name: #{asg.auto_scaling_group_name}, Instances: "\
      "#{asg.instances.count}"
    puts pretty_string
  end

  def pretty_print_launch_config(lc)
    pretty_string = "#{lc.launch_configuration_name}, #{lc.image_id},"\
      " #{lc.created_time}, #{lc.instance_type}"
    puts pretty_string
  end

  def pretty_print_snapshot(snapshot)
    pretty_string = "#{snapshot.snapshot_id}, #{snapshot.volume_id}, "\
      "#{snapshot.start_time}, #{snapshot.volume_size}, #{snapshot.tags}"
    puts pretty_string
  end

  def pretty_print_volume(volume)
    puts "#{volume.volume_id}, #{volume.create_time}, #{volume.size}"
  end
end

require_relative './awsutils'

# Utilities for cleaning up AWS AMIs
class AmiOrphans
  def initialize(awsutils)
    @awsutils = awsutils
  end

  ##
  # Returns all orphaned AMIs defined by the following conditions:
  #   * AMI must be older than a week
  #   * AMI must have no tags associated with it
  #     [Optional](defined by AWSUtils if_tag_empty attribute)
  #   * AMI must have no associated running instances
  #   * AMI must have no associated launch configuration
  #                        or
  #     Launch Configuration associated with AMI has no associated ASGS
  ##
  def orphans
    orphaned_amis = []
    amis = filter_amis(@awsutils.all_amis)

    instance_counts = @awsutils.instances_by_ami(amis)
    launch_configs = @awsutils.launch_configs_by_ami(amis)
    asgs = @awsutils.asgs_by_launch_config

    amis.each do |ami|
      ami_instances = instance_counts[ami.image_id]
      ami_launch_configs = launch_configs[ami.image_id]

      if ami_instances.empty? && ami_launch_configs.empty?
        orphaned_amis << ami
      elsif ami_instances.empty?
        ami_launch_configs.each do |launch_config|
          lc_name = launch_config.launch_configuration_name
          orphaned_amis << ami unless asgs.key?(lc_name)
        end
      else
      end

    end
    orphaned_amis
  end

  private

  def filter_amis(amis)
    results = []
    amis.each do |ami|
      if ami_older_than_week?(ami)
        if @awsutils.if_tag_empty
          results << ami if ami_no_tags?(ami)
        else
          results << ami
        end
      end
    end
    results
  end

  def ami_older_than_week?(ami)
    create_time = Time.parse(ami.creation_date)
    return true if create_time < @awsutils.stop_time
    false
  end

  def ami_no_tags?(ami)
    tags = ami.tags
    description = ami.description
    return true if tags.empty? && (description.nil? || description.empty?)
    false
  end
end

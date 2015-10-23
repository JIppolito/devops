require_relative './awsutils'

# Utilities for cleaning up AWS Auto Scaling Groups
class AutoScalingGroups
  def initialize(awsutils)
    @awsutils = awsutils
  end

  ##
  # Returns all duplicate ASGs.
  # An ASG is duplicate if it shares a launch configuration with another ASG
  ##
  def duplicates
    launch_configs = @awsutils.asgs_by_launch_config

    dupes = {}

    launch_configs.keys.each do |key|
      dupes[key] = launch_configs[key] if launch_configs[key].count > 1
    end

    dupes
  end
end

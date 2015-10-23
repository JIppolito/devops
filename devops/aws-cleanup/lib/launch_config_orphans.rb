require_relative './awsutils'

# Utilities for cleaning up AWS Launch Configurations
class LaunchConfigOrphans
  def initialize(awsutils)
    @awsutils = awsutils
  end

  ##
  # Returns all orphaned launch configurations defined by the following:
  #   * Launch Config has no associated Auto Scaling Group
  ##
  def orphans
    orphan_launch_configs = []
    launch_configs = @awsutils.all_launch_configs
    asgs = @awsutils.asgs_by_launch_config

    launch_configs.each do |launch_config|
      unless asgs.key?(launch_config.launch_configuration_name)
        orphan_launch_configs << launch_config
      end
    end
    orphan_launch_configs
  end
end

require_relative './awsutils'

# Utilities for cleaning up AWS volumes
class VolumeOrphans
  def initialize(awsutils)
    @awsutils = awsutils
  end

  ##
  # Returns all orphaned volumes defined by the following conditions:
  #   * Volume cannot have an associated snapshot
  #   * Volume cannot have an associated running instance
  ##
  def orphans
    orphans = []
    no_snapshot_volumes = []
    @awsutils.all_volumes.each do |volume|
      no_snapshot_volumes << volume if volume.snapshot_id.empty?
    end

    instances = @awsutils.instances_by_volume(no_snapshot_volumes)

    no_snapshot_volumes.each do |volume|
      orphans << volume if instances[volume.volume_id].empty?
    end

    orphans
  end
end

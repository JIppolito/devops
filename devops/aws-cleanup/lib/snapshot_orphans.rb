require_relative './awsutils'

# Utilities for cleaning up AWS Snapshots
class SnapshotOrphans
  def initialize(awsutils)
    @awsutils = awsutils
  end

  ##
  # Returns all orphaned snapshots defined by the following conditions:
  #   * Snapshot must be older than a week
  #   * Snapshot must not be associated with any running volume
  #   * Snapshot must not be associated with any exisiting AMI
  ##
  def orphans
    orphans = []
    snapshots = @awsutils.filter_snapshots_one_week(@awsutils.all_snapshots)
    volumes = @awsutils.volumes_by_snapshot(snapshots)
    amis = @awsutils.amis_by_snapshot(snapshots)

    snapshots.each do |snapshot|
      snapshot_id = snapshot.snapshot_id
      orphan_cond = volumes[snapshot_id].empty? && amis[snapshot_id].empty?
      orphans << snapshot if orphan_cond
    end

    orphans
  end
end

#!/usr/bin/ruby
require_relative '../lib/awsutils'
require_relative '../lib/ami_orphans'
require_relative '../lib/snapshot_orphans'
require_relative '../lib/launch_config_orphans'
require_relative '../lib/volume_orphans'
require_relative '../lib/auto_scaling_groups'

require 'optparse'

options = {}
OptionParser.new do |opts|
  opts.banner = 'Usage: run.rb [options]'

  opts.on(
    '-a',
    '--aws-access-key aws_access_key',
    '[Required] AWS Access Key'
  ) do |a|
    options['aws_access_key'] = a
  end

  opts.on(
    '-s',
    '--aws-secret-key aws_secret_key',
    '[Required] AWS Secret key'
  ) do |s|
    options['aws_secret_key'] = s
  end

  opts.on(
    '-r',
    '--aws-region region',
    '[Optional] AWS Secret key, default: us-east-1'
  ) do |r|
    options['region'] = r
  end

end.parse!

required_args = %w(aws_access_key aws_secret_key)
required_args.each do |arg|
  abort("Error! Missing required argument #{arg}") unless options.key?(arg)
end

options['region'] = 'us-east-1' unless options.key?('region')

awsutils = AWSUtils.new(
  options['region'],
  options['aws_access_key'],
  options['aws_secret_key']
)

puts 'Orphaned Volumes'
puts '=============================='
volume_orphans = VolumeOrphans.new(awsutils)
volumes = volume_orphans.orphans
puts "Total Orphaned Volumes: #{volumes.count}"
volumes.each do |volume|
  awsutils.pretty_print_volume(volume)
end
puts

puts 'Orphaned Launch Configurations'
puts '=============================='
launch_config_orphans = LaunchConfigOrphans.new(awsutils)
launch_configs = launch_config_orphans.orphans
puts "Total Orphaned Launch Configurations: #{launch_configs.count}"
launch_configs.each do |launch_config|
  awsutils.pretty_print_launch_config(launch_config)
end
puts

puts 'Orphaned Snapshots'
puts '=============================='
snapshot_orphans = SnapshotOrphans.new(awsutils)
snapshots = snapshot_orphans.orphans
puts "Total Orphaned Snapshots: #{snapshots.count}"
snapshots.each do |snapshot|
  awsutils.pretty_print_snapshot(snapshot)
end
puts

puts 'Orphaned AMIs'
puts '=============================='
ami_orphans = AmiOrphans.new(awsutils)
amis = ami_orphans.orphans
puts "Total Orphaned AMI's: #{amis.count}"
amis.each do |ami|
  awsutils.pretty_print_ami(ami)
end
puts

puts 'Duplicate Auto Scaling Groups'
puts '=============================='
auto_scaling_groups = AutoScalingGroups.new(awsutils)
asgs = auto_scaling_groups.duplicates
asgs.keys.each do |key|
  puts "Launch Config: #{key}"
  asgs[key].each do |asg|
    print('   ')
    awsutils.pretty_print_asg(asg)
  end
end
puts

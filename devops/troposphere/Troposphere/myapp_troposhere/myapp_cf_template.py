__author__ = 'jippolito'

import unittest
import simplejson
import troposphere.ec2 as ec2
from troposphere import Base64, Join, FindInMap, GetAtt
from troposphere import Parameter, Ref, Template
from troposphere import cloudformation, autoscaling
from troposphere.autoscaling import AutoScalingGroup, Tag
from troposphere.autoscaling import LaunchConfiguration
from troposphere.elasticloadbalancing import LoadBalancer
from troposphere.policies import UpdatePolicy, AutoScalingRollingUpdate
from troposphere.route53 import RecordSet, RecordSetGroup, AliasTarget
import troposphere.ec2 as ec2
import troposphere.elasticloadbalancing as elb

from base_autoscalinggroup import BaseAsg
from base_loadbalancer import BaseELB
from base_dns import BaseDNS
import yaml

'''
How to use this file
    This file will be unique for each team and can call the enoc_* classes to build the template with
    a template file that is the same name as this file that has the proper branch name appended.
    enoc_cf_myapp_test.py
    enoc_cf_myapp_master.yml
    enoc_cf_myapp_staging.yml
    etc.
    The logic here should be to have the branch dictate which AWS Resources are built.
'''

'''
Python Params to Pass into script:
-branch
-config file location
i.e. python enco_cf_myapp.py mybranch enoc_cf_myapp_mybranch.yml
'''
mybranch = "staging"
config_file = "/Users/jippolito/PycharmProjects/Troposphere/enoc_troposphere/enoc_cf_myapp_master.yml"


with open(config_file, 'r') as ymlfile:
    cfg = yaml.load(ymlfile)

for stack in cfg:
    print(cfg[stack])

    template = Template()
    template.add_description("Configuration for Stack: " + cfg[stack]['Name'])

    # Create Parameters
    params = cfg[stack]['Parameters']
    for param in params:
        template.add_parameter(Parameter(
            cfg[stack]['Parameters'][param]['Name'],
            Type=cfg[stack]['Parameters'][param]['Type'],
            Default=cfg[stack]['Parameters'][param]['Default'],
            Description=cfg[stack]['Parameters'][param]['Description']
        ))

    # Ensure AMI is a Parameter
    if cfg[stack]['Parameters'].has_key('AMI') is False:
        print "Ensure AMI is a Parameter as it is referenced below as Ref(AMI)"
        raise Exception("Ensure AMI is a Parameter as it is referenced below as Ref(AMI)")

    # Create Subnet And AvailZones Lists
    subnet_list=[]
    az_list=[]
    for az2sub in cfg[stack]['Region2AZSubnet']:
        subnets=cfg[stack]['Region2AZSubnet'][az2sub]['Subnets']
        az_list.append(cfg[stack]['Region2AZSubnet'][az2sub]['AZ'])
        for subnet in subnets:
            subnet_list.append(subnet)

    baseElb = BaseELB(template, cfg[stack]['ELB']['Name'], mybranch, cfg[stack]['FriendlyName'],
                      cfg[stack]['ExternalSecGrp'], subnet_list, cfg[stack]['ELB']['HealthCheck'],
                      cfg[stack]['ELB'])
    baseAsg = BaseAsg(template, mybranch, cfg[stack]['FriendlyName'], az_list, subnet_list, cfg[stack]['LaunchConfig']['Name'],
                      cfg[stack]['ASG'],
                      cfg[stack]['ELB']['Name'])
    baseDns = BaseDNS(template, mybranch, cfg[stack]['FriendlyName'], cfg[stack]['LaunchConfig']['Name'],
                      cfg[stack]['ELB']['Name'])

    # TODO: Create Launch Config class if necessary
    template.add_resource(LaunchConfiguration(
        cfg[stack]['LaunchConfig']['Name'],
        ImageId=Ref(cfg[stack]['Parameters']['AMI']['Name']),
        InstanceType=cfg[stack]['LaunchConfig']['InstanceType'],
        SecurityGroups=cfg[stack]['LaunchConfig']['SecurityGroups'],
        UserData=Base64(cfg[stack]['LaunchConfig']['UserData'])
    )
    )

    template = baseAsg.getTemplate()
    template = baseElb.getTemplate()
    template = baseDns.getTemplate()

    # TODO: Validate template by making sure it was valid json or compare it to last template and print changes, maybe???
    print(template.to_json())


class Test(unittest.TestCase):
    def setUp(self):
        pass

    def tearDown(self):
        pass

    def testName(self):
        pass


if __name__ == "__main__":
    # import sys;sys.argv = ['', 'Test.testName']
    unittest.main()

__author__ = 'jippolito'

import unittest
import json, simplejson
import troposphere.ec2 as ec2
from troposphere import Base64, Join, FindInMap
from troposphere import Parameter, Ref, Template
from troposphere import cloudformation, autoscaling
from troposphere.autoscaling import AutoScalingGroup, Tag
from troposphere.autoscaling import LaunchConfiguration
from troposphere.elasticloadbalancing import LoadBalancer
from troposphere.policies import UpdatePolicy, AutoScalingRollingUpdate
from troposphere.route53 import RecordSet, RecordSetGroup
import troposphere.ec2 as ec2
import troposphere.elasticloadbalancing as elb

class BaseAsg(object):
    def __init__(self, template, branch, friendlyName, availZone, subnet, launchConfigName, asgConfig, elb=None ):
        self.template=template
        self.branch = branch
        self.template = template
        self.friendlyName = friendlyName
        self.availZone = availZone
        self.subnet = subnet
        self.launchConfigName=launchConfigName
        self.asgConfig = asgConfig

        ''' can be null '''
        self.elb = elb

    def getTemplate(self):
        #asgConfigJson = simplejson.loads(simplejson.dumps(self.asgConfig))
        asgConfigJson=self.asgConfig
        if self.elb is not None:
            self.template.add_resource(AutoScalingGroup(
                "ELBASG",
                DesiredCapacity=asgConfigJson['DesiredCapactiy'],
                MinSize=asgConfigJson['MinSize'],
                MaxSize=asgConfigJson['MaxSize'],
                LaunchConfigurationName=self.launchConfigName,
                HealthCheckType="ELB",
                HealthCheckGracePeriod=asgConfigJson['HealthCheckGracePeriod'],
                LoadBalancerNames=[self.elb],
                AvailabilityZones=[self.availZone],
                VPCZoneIdentifier=[self.subnet],
                Tags=[
                    Tag("Name", Join("-", [ self.friendlyName, self.branch ] ), True )
                    ],
                UpdatePolicy=UpdatePolicy(
                    AutoScalingRollingUpdate=AutoScalingRollingUpdate(
                        MaxBatchSize=asgConfigJson['MaxBatchSize'],
                        MinInstancesInService=asgConfigJson['MinInstancesInService'],
                        PauseTime=asgConfigJson['PauseTime'],
                        WaitOnResourceSignals=asgConfigJson['WaitOnResourceSignals']
                        )
                    )
            )
            )
        else:
            self.template.add_resource(AutoScalingGroup(
                "NoELBASG",
                DesiredCapacity=asgConfigJson['DesiredCapactiy'],
                MinSize=asgConfigJson['MinSize'],
                MaxSize=asgConfigJson['MaxSize'],
                LaunchConfigurationName=self.launchConfigName,
                HealthCheckType="EC2",
                HealthCheckGracePeriod=asgConfigJson['HealthCheckGracePeriod'],
                AvailabilityZones=[self.availZone],
                VPCZoneIdentifier=[self.subnet],
                Tags=[
                    Tag("Name", Join("-", [ self.friendlyName, self.branch ] ), True )
                    ],
                UpdatePolicy=UpdatePolicy(
                    AutoScalingRollingUpdate=AutoScalingRollingUpdate(
                        MaxBatchSize=asgConfigJson['MaxBatchSize'],
                        MinInstancesInService=asgConfigJson['MinInstancesInService'],
                        PauseTime=asgConfigJson['PauseTime'],
                        WaitOnResourceSignals=asgConfigJson['WaitOnResourceSignals']
                        )

                    )
            )
            )

        return self.template

class Test(unittest.TestCase):


    def setUp(self):
        pass


    def tearDown(self):
        pass


    def testName(self):
        pass


if __name__ == "__main__":
    #import sys;sys.argv = ['', 'Test.testName']
    unittest.main()
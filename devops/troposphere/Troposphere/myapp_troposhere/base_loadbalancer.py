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


class BaseELB(object):
    def __init__(self, template, elbname, branch, friendlyName, externalSecurityGroup, subnet, elbHealthCheckConfig,
                 elbListenerConfig=None):

        # TODO: Add validation here to ensure it has valid params
        self.branch = branch
        self.template = template
        self.elbname=elbname
        self.friendlyName = friendlyName
        self.externalSecurityGroup = externalSecurityGroup
        self.subnet = subnet
        self.elbHealthCheckConfig = elbHealthCheckConfig

        '''elbListenerConfig can be null'''
        self.elbListenerConfig = elbListenerConfig
        if self.elbListenerConfig.has_key('Timeout'):
            self.connectionDrainingPolicyTimeout = self.elbListenerConfig['Timeout']
            self.connectionDrainingPolicy = True

        else:
            self.connectionDrainingPolicy = False

    def validateAsgHealthCheckConfig(self, asgHealthCheckConfig):
        pass

    def validateElbListenerConfig(self, elbListenerConfig):
        pass

    def validateSslCert(self, sslCert):
        pass

    def getConnectionDrainingPolicy(self):
        if self.connectionDrainingPolicy is True:
            return elb.ConnectionDrainingPolicy(Enabled=True, Timeout=self.connectionDrainingPolicy)
        else:
            return elb.ConnectionDrainingPolicy(Enabled=False)

    def getELBListener(self):

        if self.elbListenerConfig is not None:
            #elbListenerJson = simplejson.loads(simplejson.dumps(self.elbListenerConfig))
            if self.elbListenerConfig.has_key('SSLCert'):
                return elb.Listener(LoadBalancerPort=self.elbListenerConfig['LoadBalancerPort'],
                                    InstancePort=self.elbListenerConfig['InstancePort'], Protocol=self.elbListenerConfig['Protocol'],
                                    InstanceProtocol=self.elbListenerConfig['InstanceProtocol'], SSLCertificateId=self.elbListenerConfig['SSLCert'])
            else:
                return elb.Listener(LoadBalancerPort=self.elbListenerConfig['LoadBalancerPort'],
                                    InstancePort=self.elbListenerConfig['InstancePort'], Protocol=self.elbListenerConfig['Protocol'],
                                    InstanceProtocol=self.elbListenerConfig['InstanceProtocol'])

        else:
            # Default ELB Listener
            loadBalancerPort = "80"
            instancePort = "8080"
            protocol = "HTTP"
            instanceProtocol = "HTTP"

            return elb.Listener(LoadBalancerPort=loadBalancerPort, InstancePort=instancePort, Protocol=protocol, InstanceProtocol=instanceProtocol)

    def getTemplate(self):
        #elbHealthCheckJson = simplejson.loads(simplejson.dumps(self.elbHealthCheckConfig))
        self.template.add_resource(LoadBalancer(
            self.elbname,
            ConnectionDrainingPolicy=self.getConnectionDrainingPolicy(),
            Subnets=[self.subnet],
            HealthCheck=elb.HealthCheck(
                Target=self.elbHealthCheckConfig['Target'],
                HealthyThreshold=self.elbHealthCheckConfig['HealthyThreshold'],
                UnhealthyThreshold=self.elbHealthCheckConfig['UnhealthyThreshold'],
                Interval=self.elbHealthCheckConfig['Interval'],
                Timeout=self.elbHealthCheckConfig['Timeout'],
            ),
            Listeners=[
                self.getELBListener(),
            ],
            Scheme="internal",
            SecurityGroups=[self.externalSecurityGroup],
            LoadBalancerName=Join("_", [ self.friendlyName, self.branch ] ),
            CrossZone=True,
        ))

        return self.template


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

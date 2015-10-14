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
                 elbListenerConfig=None, connectionDrainingPolicyTimeout=60):
        self.branch = branch
        self.template = template
        self.elbname=elbname
        self.friendlyName = friendlyName
        self.externalSecurityGroup = externalSecurityGroup
        self.subnet = subnet
        self.elbHealthCheckConfig = elbHealthCheckConfig

        '''elbListenerConfig and sslCert can be null'''
        self.elbListenerConfig = elbListenerConfig
        self.connectionDrainingPolicyTimeout = connectionDrainingPolicyTimeout
        self.connectionDrainingPolicy = True

        if connectionDrainingPolicyTimeout is None:
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
            elbListenerJson=self.elbListenerConfig
            if elbListenerJson.has_key('sslCert'):
                return elb.Listener(LoadBalancerPort=elbListenerJson['LoadBalancerPort'],
                                    InstancePort=elbListenerJson['InstancePort'], Protocol=elbListenerJson['Protocol'],
                                    InstanceProtocol=elbListenerJson['InstanceProtocol'], SSLCertificateId=elbListenerJson['sslCert'])
            else:
                return elb.Listener(LoadBalancerPort=elbListenerJson['LoadBalancerPort'],
                                    InstancePort=elbListenerJson['InstancePort'], Protocol=elbListenerJson['Protocol'],
                                    InstanceProtocol=elbListenerJson['InstanceProtocol'])

        else:
            # Default ELB Listener
            loadBalancerPort = "80"
            instancePort = "8080"
            protocol = "HTTP"
            instanceProtocol = "HTTP"

            return elb.Listener(LoadBalancerPort=loadBalancerPort, InstancePort=instancePort, Protocol=protocol, InstanceProtocol=instanceProtocol)

    def getTemplate(self):
        elbHealthCheckJson = simplejson.loads(simplejson.dumps(self.elbHealthCheckConfig))

        self.template.add_resource(LoadBalancer(
            self.elbname,
            ConnectionDrainingPolicy=self.getConnectionDrainingPolicy(),
            Subnets=[self.subnet],
            HealthCheck=elb.HealthCheck(
                Target=elbHealthCheckJson['Target'],
                HealthyThreshold=elbHealthCheckJson['HealthyThreshold'],
                UnhealthyThreshold=elbHealthCheckJson['UnhealthyThreshold'],
                Interval=elbHealthCheckJson['Interval'],
                Timeout=elbHealthCheckJson['Timeout'],
            ),
            Listeners=[
                self.getELBListener(),
            ],
            Scheme="internal",
            SecurityGroups=[self.externalSecurityGroup],
            LoadBalancerName=Join("_", [self.friendlyName, self.branch, "lb"]),
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

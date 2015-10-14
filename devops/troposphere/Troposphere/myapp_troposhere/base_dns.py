__author__ = 'jippolito'

import unittest
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


class BaseDNS(object):
    # TODO: Add validation so that either elbName is None OR instance IPs are None, but not both
    def __init__(self, template, branch, friendlyName, projectBaseURL, elbName=None, launchConfigName=None ):
        self.template=template
        self.branch=branch
        self.friendlyName=friendlyName
        self.projectBaseURL=projectBaseURL

        # May be None
        self.elbName=elbName
        self.launchConfigName=launchConfigName


    def getTemplate(self):
        if self.elbName is not None:
            hostedZoneId=GetAtt(self.elbName,'CanonicalHostedZoneNameID')
            dnsName=GetAtt(self.elbName,'DNSName')
            aliasTarget=AliasTarget(hostedZoneId, dnsName)

            self.template.add_resource(RecordSetGroup(
            "DNS",
            HostedZoneName=Join(".", [ self.projectBaseURL, ""]),
            RecordSets=[
                RecordSet(
                    SetIdentifier=Join(" ", [ self.friendlyName, self.branch, self.projectBaseURL ] ),
                    Name=Join(" ", [ self.friendlyName, self.branch, self.projectBaseURL ] ),
                    Type="A",
                    AliasTarget=aliasTarget
                )
            ]))
        else:
            self.template.add_resource(RecordSetGroup(
            "DNS",
            HostedZoneName=Join(".", [ self.projectBaseURL, ""]),
            RecordSets=[
                RecordSet(
                    SetIdentifier=Join(" ", [ self.friendlyName, self.branch, self.projectBaseURL ] ),
                    Name=Join(" ", [ self.friendlyName, self.branch, self.projectBaseURL ] ),
                    Type="A",
                    ResourceRecords=[ GetAtt(self.launchConfigName, 'PublicIp')]
                )
            ]))

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
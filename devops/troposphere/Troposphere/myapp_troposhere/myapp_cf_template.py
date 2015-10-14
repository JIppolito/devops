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
from enoc_troposhere.base_autoscalinggroup import BaseAsg
from enoc_troposhere.base_loadbalancer import BaseELB
from enoc_troposhere.base_dns import BaseDNS


# TODO Python Params to Pass In
'''
-branch
-elbListenerConfig File Location
-LaunchConfig File Location
-elbHealthCheckConfig File Location
-Region2Subnet File Location
-asgConfig File Location
'''

launchConfigName="LaunchConfig"
mybranch="staging"
elbName="elb"
friendlyName="myapp"
externalSecurityGroup="sg-somegroup"

launchConfig={
    'launchConfigName':'LaunchConfig',
    'InstanceType': 't2.small',
    'SecurityGroups':['sg-somegroup'],
    'UserData': 'myapp''
}

elbListenerConfig={
    'LoadBalancerPort': '443',
    'InstancePort': '8888',
    'Protocol': 'HTTPS',
    'InstanceProtocol': 'HTTP',
    'sslCert': 'myarnd:server-certificate/.myapp.company.com'
}

elbHealthCheckConfig={
   'Target':"TCP:8888/",
   'HealthyThreshold':"5",
   'UnhealthyThreshold':"2",
   'Interval':"20",
   'Timeout':"15"
}

asgConfig={
    'MinSize': '1',
    'MaxSize': '1',
    'DesiredCapactiy': '1',
    'HealthCheckGracePeriod': '180',
    'MaxBatchSize': '1',
    'MinInstancesInService': '0',
    'PauseTime': 'PT0S',
    'WaitOnResourceSignals': 'False'
}

template = Template()
template.add_description("Configures Stack for " + friendlyName)

# TODO: Fix subnet and avail zones to be a better format
subnet_list_prd=["subnet-eas1b", "subnet-east1c", "subnet-east1d"]
az_list_prd= ["us-east-1b", "us-east-1c", "us-east-1d"]

template.add_mapping('Region2SubnetDev', {
    "us-east-1": {  "Subnet" : subnet_list_prd ,
                    "AZ": az_list_prd
}})

subnet_list=[ "subnet-east1b", "subnet-east1c", "subnet-east1d" ]
az_list=[ "us-east-1b", "us-east-1c", "us-east-1d"]

template.add_mapping('Region2SubnetProd', {
    "us-east-1": { "Subnet" : subnet_list,
                    "AZ": az_list
}})


AMI = template.add_parameter(Parameter(
    "AMI",
    Type="String",
    Default="ami-c2100aaa",
    Description="Image generated for this build",
))

Region = template.add_parameter(Parameter(
    "Region",
    Type="String",
    Default="us-east-1",
    Description="Region to deploy into.",
))

GITCOMMIT = template.add_parameter(Parameter(
    "GITCOMMIT",
    Type="String",
    Default="1",
    Description="The revision number of the sub stack template uploaded to S3",
))

GITBRANCH = template.add_parameter(Parameter(
    "GITBRANCH",
    Type="String",
    Default="dev",
    Description="The revision number of the sub stack template uploaded to S3.",
))


ExternalSecurityGroup = template.add_parameter(Parameter(
    "ExternalSecurityGroup",
    Type="String",
    Default=externalSecurityGroup,
    Description="Security group to define user access to the stack.",
))

InstanceType = template.add_parameter(Parameter(
    "InstanceType",
    Type="String",
    Default="t2.small",
    Description="The EC2 instance type to launch.",
))

SecurityGroup = template.add_parameter(Parameter(
    "SecurityGroup",
    Type="String",
    Description="Security group for full stack.",
))

ServiceName = template.add_parameter(Parameter(
    "ServiceName",
    Type="String",
    Default="app",
    Description="The name of the service deployed into the container, as propagated to userdata.",
))

BranchName = template.add_parameter(Parameter(
    "BranchName",
    Type="String",
    Default=mybranch,
    Description="The name of the specific branch from the git repository for this service.",
))

ProjectBaseURL = template.add_parameter(Parameter(
    "ProjectBaseURL",
    Type="String",
    Description="The base URL of the service being deployed.",
))

if mybranch is "master" or mybranch is "staging":
    subnet=FindInMap( "Region2SubnetProd", Ref(Region), "Subnet" )
    AZ=FindInMap( "Region2SubnetProd", Ref(Region), "AZ" )
else:
    subnet=FindInMap( "Region2SubnetDev", Ref(Region), "Subnet" )
    AZ=FindInMap( "Region2SubnetDev", Ref(Region), "AZ" )

enocElb=EnocELB(template, elbName, mybranch, friendlyName, externalSecurityGroup, subnet, elbHealthCheckConfig,
                 elbListenerConfig, connectionDrainingPolicyTimeout=120)

template=enocElb.getTemplate()

LaunchConfig=template.add_resource(LaunchConfiguration(
    launchConfigName,
    ImageId=Ref(AMI),
    InstanceType=Ref(InstanceType),
    SecurityGroups=[Ref(SecurityGroup)],
    UserData=Base64(Ref(ServiceName))
    )
)

enocAsg=EnocAsg(template, mybranch, friendlyName, AZ, subnet, launchConfigName, asgConfig, elbName)
template=enocAsg.getTemplate()

enocDNS=EnocDNS(template, mybranch, friendlyName, launchConfigName, elbName)
template=enocDNS.getTemplate()

# TODO: Validate template by making sure it was valid json or compare it to last template and print changes to ensure
print(template.to_json())


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
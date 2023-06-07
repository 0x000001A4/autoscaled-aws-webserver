#!/bin/bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"

#export PATH=<path to aws installation>:$PATH
export AWS_DEFAULT_REGION=us-east-1
export AWS_ACCOUNT_ID=728921769838
export AWS_ACCESS_KEY_ID=AKIA2TNY7Q5XMHUZ4YLB
export AWS_SECRET_ACCESS_KEY=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
export AWS_EC2_SSH_KEYPAIR_PATH=/home/velhinho/Desktop/cnv/cnv-shared/cnv/aws.pem
export AWS_SG_ID=sg-1e8f9e1f
export AWS_KEYPAIR_NAME=aws

export AWS_AMI_NAME=cnv-awslab-webserver-image
export AWS_ASG_NAME=cnv-awslab-webserver-autoscaling
export AWS_ASG_CONFIG_NAME=cnv-awslab-webserver-config
export AWS_LB_NAME=cnv-awslab-webserver-lb

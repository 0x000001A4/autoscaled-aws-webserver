#!/bin/bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"

#export PATH=<path to aws installation>:$PATH
export AWS_DEFAULT_REGION=eu-west-1
export AWS_ACCOUNT_ID=444227009757
export AWS_ACCESS_KEY_ID=AKIAWO3P6HTO45JPDUUS
export AWS_SECRET_ACCESS_KEY=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
export AWS_EC2_SSH_KEYPAIR_PATH=/home/ricky420/school/cnv/cnv/keypair.pem
export AWS_SG_ID=sg-0d889cf737eea3b6c
export AWS_KEYPAIR_NAME=keypair

export AWS_AMI_NAME=cnv-awslab-webserver-image
export AWS_ASG_NAME=cnv-awslab-webserver-autoscaling
export AWS_ASG_CONFIG_NAME=cnv-awslab-webserver-config
export AWS_LB_NAME=cnv-awslab-webserver-lb

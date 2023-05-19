#!/bin/bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"

#export PATH=<path to aws installation>:$PATH
export AWS_DEFAULT_REGION=us-east-1
export AWS_ACCOUNT_ID=763371698103
export AWS_ACCESS_KEY_ID=AKIA3DPD7W633JJDFMXN
export AWS_SECRET_ACCESS_KEY=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
export AWS_EC2_SSH_KEYPAIR_PATH=/home/ricky420/school/cnv/cnv/keypair.pem
export AWS_SG_ID=sg-0e99c75e2e2f55e2a
export AWS_KEYPAIR_NAME=keypair

export AWS_AMI_NAME=cnv-awslab-webserver-image
export AWS_ASG_NAME=cnv-awslab-webserver-autoscaling
export AWS_ASG_CONFIG_NAME=cnv-awslab-webserver-config
export AWS_LB_NAME=cnv-awslab-webserver-lb
export AWS_LB_SG_ID=sg-0fd70d9f4b6e33622
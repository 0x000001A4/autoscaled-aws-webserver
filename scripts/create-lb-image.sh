#!/bin/bash

source config.sh

# Step 1: launch a vm instance.
$DIR/launch-vm.sh

# Step 2: install software in the VM instance.
$DIR/install-lb.sh

# Step 3: test VM instance.
$DIR/test-vm.sh

# Step 4: create VM image (AMI).
echo "New VM image with id $(aws ec2 create-image --instance-id $(cat instance.id) --name $AWS_AMI_NAME | jq -r .ImageId)."

# Step 5: Wait for image to become available.
echo "Waiting for image to be ready... (this can take a couple of minutes)"
aws ec2 wait image-available --filters Name=name,Values=$AWS_AMI_NAME
echo "Waiting for image to be ready... done! \o/"
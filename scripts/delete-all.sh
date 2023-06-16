#!/bin/bash

source config.sh

function check_file {
    if [ $# -lt 1 ]; then
        echo "check_file with no arguments :("
        exit
    fi

    if [ ! -f $1 ]; then
        echo "No file '$1' !!!"
        exit
    fi
}

check_file "instance.id"

# Terminate the VM instance
echo "Terminating the VM instance with id $(cat instance.id)..."
aws ec2 terminate-instances --instance-ids $(cat instance.id)
echo "Done terminating!"

check_file "image.id"

# Retrieve all the snapshots associated with the AMI (there should be only one? xd)
echo "Getting the snapshot id associated with the AMI with id $(cat image.id)..."
aws ec2 describe-images \
    --image-ids $(cat image.id) \
    --query 'Images[:].BlockDeviceMappings[:].Ebs.SnapshotId' \
    --output text > snapshot.id
if [ ! -s snapshot.id ]; then
    echo "Got no snapshot! Either the AMI has no snapshots or something went wrong..."
else
    echo "Got $(cat snapshot.id)!"
fi

# Deregister the AMI
echo "Deregistering the AMI with id $(cat image.id)..."
aws ec2 deregister-image --image-id $(cat image.id)
if [ $? -ne 0 ]; then
    echo "Something went wrong while deregistering the AMI! Probably the AMI doesn't exist anymore or the id is wrong..."
    exit
fi
echo "Done deregistering!"

# Delete the snapshots
echo "Deleting the snapshot with id $(cat snapshot.id)..."
aws ec2 delete-snapshot --snapshot-id $(cat snapshot.id)
echo "Done deleting!"


# Remove all the temporary files
echo "Removing temporary files..."
rm instance.id image.id snapshot.id instance.dns

echo "Removing lambdas..."
./deregister-lambdas.sh
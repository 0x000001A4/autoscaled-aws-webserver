#!/bin/bash

source config.sh

# Run new instance.
aws ec2 run-instances \
    --image-id resolve:ssm:/aws/service/ami-amazon-linux-latest/amzn2-ami-hvm-x86_64-gp2 \
    --instance-type t2.micro \
    --key-name "${AWS_KEYPAIR_NAME}" \
    --security-group-ids "${AWS_SG_ID}" \
    --monitoring Enabled=true \
    --query "Instances[0].InstanceId" \
    --output text | tr -d '\r\n' > instance.id
echo "New instance with id $(cat instance.id)."

# Wait for instance to be running.
aws ec2 wait instance-running --instance-id $(cat instance.id)
echo "New instance with id $(cat instance.id) is now running."

# Extract DNS nane.
aws ec2 describe-instances \
    --instance-id $(cat instance.id) \
    --query "Reservations[0].Instances[0].NetworkInterfaces[0].PrivateIpAddresses[0].Association.PublicDnsName" \
    --output text > instance.dns
echo "New instance with id $(cat instance.id) has address $(cat instance.dns)."

# Wait for instance to have SSH ready.
until ssh -q -o "StrictHostKeyChecking=no" -o "UserKnownHostsFile=/dev/null" -i "${AWS_EC2_SSH_KEYPAIR_PATH}" ec2-user@$(cat instance.dns) exit; do
    echo "Waiting for $(cat instance.dns):22 (SSH)..."
    sleep 0.5
done
echo "New instance with id $(cat instance.id) is ready for SSH access."

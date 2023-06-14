#!/bin/bash

source config.sh

# Install java.
cmd="set -v"
cmd="$cmd; sudo yum update -y; sudo yum install java-11-amazon-corretto.x86_64 -y"
cmd="$cmd; java -version"
cmd="$cmd; curl -s "https://get.sdkman.io" | bash"
cmd="$cmd; source '/home/ec2-user/.sdkman/bin/sdkman-init.sh'"
cmd="$cmd; sdk install maven"
cmd="$cmd; mvn --version;"
ssh -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAIR_PATH ec2-user@$(cat instance.dns) $cmd



# Send project directory to AWS instance.
scp -r -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAIR_PATH $DIR/../res ec2-user@$(cat instance.dns):
scp -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAIR_PATH $DIR/../scripts/image.id ec2-user@$(cat instance.dns):
scp -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAIR_PATH $DIR/../scripts/awsconfig.sh ec2-user@$(cat instance.dns):


# Install project dependencies.
cmd="source awsconfig.sh; cd ~ec2-user/res; mvn clean install; mvn compile"

ssh -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAIR_PATH ec2-user@$(cat instance.dns) $cmd



# Setup auto-start web server loadbalancer.service
cmd="touch loadbalancer.service"
cmd="$cmd; printf \"[Unit]\n
 Description=Loadbalancer Server\n
 After=network.target\n\n[Service]\n
 User=ec2-user\n
 Type=simple\n
 EnvironmentFile=/home/ec2-user/awsconfig.sh\n
 WorkingDirectory=/home/ec2-user/res/loadbalancer\n
 ExecStart=/home/ec2-user/.sdkman/candidates/maven/current/bin/mvn exec:java -Dexec.mainClass=pt.ulisboa.tecnico.cnv.loadbalancer.LoadBalancer\n
 SuccessExitStatus=143\n
 TimeoutStopSec=10\n
 RemainAfterExit=no\n\n[Install]\n
 WantedBy=multi-user.target
\" > loadbalancer.service"
cmd="$cmd; sudo mv loadbalancer.service /etc/systemd/system/loadbalancer.service"
cmd="$cmd; sudo chmod 644 /etc/systemd/system/loadbalancer.service"

ssh -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAIR_PATH ec2-user@$(cat instance.dns) $cmd


# Enable the loadbalancer.service to auto-start the webserver
cmd="source awsconfig.sh; sudo systemctl enable loadbalancer.service"
cmd="$cmd; sudo systemctl start loadbalancer.service"
cmd="$cmd; sudo systemctl status loadbalancer.service"

ssh -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAIR_PATH ec2-user@$(cat instance.dns) $cmd

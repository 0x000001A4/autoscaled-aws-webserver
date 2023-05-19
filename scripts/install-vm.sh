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

# Install project dependencies.
cmd="cd res; mvn clean install; mvn compile"
ssh -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAIR_PATH ec2-user@$(cat instance.dns) $cmd 

# Setup web server to start on instance launch.
cmd="cd res/loadbalancer"
cmd="$cmd; echo \"mvn exec:java -Dexec.mainClass=\"pt.ulisboa.tecnico.cnv.loadbalancer.LoadBalancer\"\" | sudo tee -a /etc/rc.local; sudo chmod +x /etc/rc.local"
ssh -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAIR_PATH ec2-user@$(cat instance.dns) $cmd

#!/bin/bash
source config.sh

# Install java.
cmd="set -v"
cmd="$cmd; sudo yum update -y; sudo yum install java-11-amazon-corretto.x86_64 -y"
cmd="$cmd; java -version"
cmd="$cmd; curl -s "https://get.sdkman.io" | bash"
cmd="$cmd; source '/home/ec2-user/.sdkman/bin/sdkman-init.sh'"
cmd="$cmd; sdk install maven"
cmd="$cmd; mvn --version"
ssh -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAIR_PATH ec2-user@$(cat instance.dns) $cmd



# Send project directory to AWS instance.
scp -r -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAIR_PATH $DIR/../res ec2-user@$(cat instance.dns):
scp -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAIR_PATH $DIR/../scripts/awsconfig.sh ec2-user@$(cat instance.dns):



# Install project dependencies.
cmd="cd ~ec2-user/res; mvn clean install compile package"
ssh -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAIR_PATH ec2-user@$(cat instance.dns) $cmd 



# Setup auto-start web server worker.service
cmd="touch worker.service"
cmd="$cmd; printf \"[Unit]\n
 Description=Worker Server\n
 After=network.target\n\n[Service]\n
 User=ec2-user\n
 Type=simple\n
 EnvironmentFile=/home/ec2-user/awsconfig.sh\n
 WorkingDirectory=/home/ec2-user/res\n
 ExecStart=/usr/bin/java -cp /home/ec2-user/res/webserver/target/webserver-1.0.0-SNAPSHOT-jar-with-dependencies.jar -javaagent:instrumentation/target/JavassistWrapper-1.0-jar-with-dependencies.jar=PrintMetrics:pt.ulisboa.tecnico.cnv:output  pt.ulisboa.tecnico.cnv.webserver.WebServer\n
 SuccessExitStatus=143\n
 TimeoutStopSec=10\n
 RemainAfterExit=no\n\n[Install]\n
 WantedBy=multi-user.target
\" > worker.service"
cmd="$cmd; sudo mv worker.service /etc/systemd/system/worker.service"

ssh -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAIR_PATH ec2-user@$(cat instance.dns) $cmd


# Enable the worker.service to auto-start the webserver
cmd="sudo systemctl enable worker.service"
cmd="$cmd; sudo systemctl start worker.service"
cmd="$cmd; sudo systemctl status worker.service"

ssh -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAIR_PATH ec2-user@$(cat instance.dns) $cmd

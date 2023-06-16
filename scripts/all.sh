#!/bin/bash

# This script launches an AWS instance, installs the web server (workers) software and creates an image (AMI) of it.
# This image will be used to launch the web server instances in the load balancer / autoscaler.
# After the image is done, it uploads the load balancer and autoscaler code and it becomes the load balancer.

source config.sh

# Paths
## Res folder
RES_DIR="${DIR}/../res"
## Web Server
WEBSERVER_DIR="${RES_DIR}/Web Server/target"
WEBSERVER_JAR="webserver-1.0.0-SNAPSHOT-jar-with-dependencies.jar"
## Load Balancer
LOADBALANCER_DIR="${RES_DIR}/Load Balancer/target"
LOADBALANCER_JAR="loadbalancer-1.0.0-SNAPSHOT-jar-with-dependencies.jar"
## Javassist
JAVASSIST_DIR="${RES_DIR}/instrumentation/target"
JAVASSIST_JAR="JavassistWrapper-1.0-jar-with-dependencies.jar"


if ! command -v mvn &> /dev/null
then
    echo "Maven could not be found. Please install it and try again."
    exit
fi

function setupService {
    if [ $# -ne 2 ]
    then
        echo "setupService: Invalid number of arguments."
        echo "Usage: setupService <service_name> <exec_start>"

        exit 1
    fi
    service_name=$(echo $1 | awk '{ print tolower($0); }')
    service_file_name="${service_name}.service"
    description="$(echo ${1^}) Server"
    exec_start=$2
    echo -n "printf '[Unit]\n
 Description=${description}\n
 After=network.target\n
\n[Service]\n
 User=ec2-user\n
 Type=simple\n
 EnvironmentFile=/home/ec2-user/awsconfig.sh\n
 WorkingDirectory=/home/ec2-user\n
 ExecStart=${exec_start}\n
 SuccessExitStatus=143\n
 TimeoutStopSec=10\n
 RemainAfterExit=no\n
\n[Install]\n
 WantedBy=multi-user.target
' > ${service_file_name}" | tr -d '\n'

    echo -n ";sudo mv ${service_file_name} /etc/systemd/system/${service_file_name}"
    echo -n ";sudo chmod 644 /etc/systemd/system/${service_file_name}"

    # Enable the worker.service to auto-start the webserver
    echo -n "; sudo systemctl enable ${service_file_name}"
    echo -n "; sudo systemctl start ${service_file_name}"
    echo -n "; sudo systemctl status ${service_file_name}"
}


# Step 1: Launch a vm instance in background.
${DIR}/launch-vm.sh &


# Step 2: Build the project.
mvn -f "${RES_DIR}" clean package | grep -E '\[[1-9]+/[1-9]+\]$'

if [ ${PIPESTATUS[0]} -ne 0 ]
then
    echo "Maven failed to build the project. Please fix the errors and try again."
    # Wait for previously launched instance to terminate.
    echo "Waiting for image to be ready so we can terminate it..."
    wait $(jobs -rp)
    echo "Terminating instance $(cat instance.id)..."
    aws ec2 terminate-instances --instance-ids $(cat instance.id)
    exit 1
fi


# Step 3: install software in the VM instance.
cmd="set -v" # It can be empty, but it must be something.

## Wait for previously launched instance to have SSH ready.
wait $(jobs -rp)

## Send config file to AWS instance.
scp -o "StrictHostKeyChecking=no" -o "UserKnownHostsFile=/dev/null" -i "${AWS_EC2_SSH_KEYPAIR_PATH}" "${DIR}/awsconfig.sh" ec2-user@$(cat instance.dns):
scp -o "StrictHostKeyChecking=no" -o "UserKnownHostsFile=/dev/null" -i "${AWS_EC2_SSH_KEYPAIR_PATH}" "${DIR}/cputracker.sh" ec2-user@$(cat instance.dns):

## Send web server jar to AWS instance.
scp -o "StrictHostKeyChecking=no" -o "UserKnownHostsFile=/dev/null" -i "${AWS_EC2_SSH_KEYPAIR_PATH}" "${WEBSERVER_DIR}/${WEBSERVER_JAR}" ec2-user@$(cat instance.dns):
scp -o "StrictHostKeyChecking=no" -o "UserKnownHostsFile=/dev/null" -i "${AWS_EC2_SSH_KEYPAIR_PATH}" "${JAVASSIST_DIR}/${JAVASSIST_JAR}" ec2-user@$(cat instance.dns):

## Install java.
cmd="$cmd; sudo yum update -y; sudo yum install java-11-amazon-corretto.x86_64 -y"
cmd="$cmd; java -version"

## Setup auto-start web server worker.service
cmd="$cmd; $(setupService "worker" "/usr/bin/java -cp \"${WEBSERVER_JAR}\" -javaagent:${JAVASSIST_JAR}=PrintMetrics:pt.ulisboa.tecnico.cnv:output pt.ulisboa.tecnico.cnv.webserver.WebServer")"

## Run commands in AWS instance.
ssh -o "StrictHostKeyChecking=no" -o "UserKnownHostsFile=/dev/null" -i "${AWS_EC2_SSH_KEYPAIR_PATH}" ec2-user@$(cat instance.dns) ${cmd}

# Step 4: test VM instance.
${DIR}/test-vm.sh

# Step 5: create VM image (AMI).
aws ec2 create-image \
    --instance-id "$(cat instance.id)" \
    --name "${AWS_AMI_NAME}" \
    --query "ImageId" \
    --output text > "${DIR}/image.id"
echo "New VM image with id $(cat image.id)."


# Step 6: Wait for image to become available.
echo "Waiting for image to be ready... (this can take a couple of minutes)"
aws ec2 wait image-available --image-id $(cat image.id)
echo "Waiting for image to be ready... done! \o/"


# Step 7: Remove stuff to get ready for load balancer.
cmd="set -v" # It can be empty, but it must be something.

## Disable the worker.service to auto-start the webserver
cmd="$cmd; sudo systemctl stop worker.service"
cmd="$cmd; sudo systemctl kill worker.service"
cmd="$cmd; sudo systemctl disable worker.service"
cmd="$cmd; sudo systemctl status worker.service"
cmd="$cmd; sudo rm /etc/systemd/system/worker.service"

## Remove web server jar from AWS instance.
cmd="$cmd; rm \"/home/ec2-user/${WEBSERVER_JAR}\""
cmd="$cmd; rm \"/home/ec2-user/${JAVASSIST_JAR}\""


# Step 8: Upload load balancer and autoscaler code.

## Send load balancer jar to AWS instance.
scp -o "StrictHostKeyChecking=no" -o "UserKnownHostsFile=/dev/null" -i "${AWS_EC2_SSH_KEYPAIR_PATH}" "${LOADBALANCER_DIR}/${LOADBALANCER_JAR}" ec2-user@$(cat instance.dns):

## Send AMI id to AWS instance, so autoscaler can use it.
scp -o "StrictHostKeyChecking=no" -o "UserKnownHostsFile=/dev/null" -i "${AWS_EC2_SSH_KEYPAIR_PATH}" "${DIR}/image.id" ec2-user@$(cat instance.dns):

## Setup auto-start web server loadbalancer.service
cmd="$cmd; $(setupService "LoadBalancer" "/usr/bin/java -jar \"${LOADBALANCER_JAR}\"")"

## Run commands in AWS instance.
ssh -o "StrictHostKeyChecking=no" -o "UserKnownHostsFile=/dev/null" -i "${AWS_EC2_SSH_KEYPAIR_PATH}" ec2-user@$(cat instance.dns) ${cmd}

echo "Done! Load Balancer is running at $(cat instance.dns):8000"

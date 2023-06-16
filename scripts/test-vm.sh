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

# Requesting an instance reboot.
aws ec2 reboot-instances --instance-ids $(cat instance.id)
echo "Rebooting instance to test web server auto-start."

check_file "instance.dns"

# Letting the instance shutdown.
sleep 5

url="$(cat instance.dns):8000"

# Wait for port 8000 to become available.
until curl --silent --output /dev/null "${url}"; do
    echo "Waiting for $(cat instance.dns):8000..."
    sleep 0.5
done

# Sending a query!
echo "Sending a query!"
curl "${url}/test?testing=after-reboot&instanceId=$(cat instance.id)"

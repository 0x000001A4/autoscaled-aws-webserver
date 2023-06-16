@echo off
if not exist ..\scripts\instance.dns (
    echo No instance.dns file
    goto :eof
)

set /p DNS=<..\scripts\instance.dns

scp -o "StrictHostKeyChecking=no" -o "UserKnownHostsFile=/dev/null" -i ..\keypair.pem "Load Balancer\target\loadbalancer-1.0.0-SNAPSHOT-jar-with-dependencies.jar" ec2-user@%DNS%:~/loadbalancer.jar

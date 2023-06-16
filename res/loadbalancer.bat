@echo off
if not exist ..\scripts\instance.dns (
    echo No instance.dns file
    goto :eof
)

set /p DNS=<..\scripts\instance.dns

ssh -o "StrictHostKeyChecking=no" -o "UserKnownHostsFile=/dev/null" -i ..\keypair.pem ec2-user@%DNS% %*

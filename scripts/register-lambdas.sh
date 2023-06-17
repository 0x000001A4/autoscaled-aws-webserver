#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)"
source ${SCRIPT_DIR}/config.sh


echo "Creating role for lambdas..."

aws iam create-role \
	--role-name lambda-role \
	--assume-role-policy-document '{"Version": "2012-10-17","Statement": [{ "Effect": "Allow", "Principal": {"Service": "lambda.amazonaws.com"}, "Action": "sts:AssumeRole"}]}' > /dev/null

echo "Done!"


echo "Attaching role to lambda execution policy..."

aws iam attach-role-policy \
	--role-name lambda-role \
	--policy-arn arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole > /dev/null

sleep 5 # Sleep to give time for policy to start working
echo "Done!"


echo "Registering Image Compression lambda..."

aws lambda create-function \
	--function-name compressimage-lambda \
	--zip-file fileb://../res/"Image Compression"/target/compression-1.0.0-SNAPSHOT-jar-with-dependencies.jar \
	--handler pt.ulisboa.tecnico.cnv.compression.CompressImageHandlerImpl \
	--runtime java11 \
	--timeout 60 \
	--memory-size 512 \
	--role arn:aws:iam::${AWS_ACCOUNT_ID}:role/lambda-role > /dev/null

echo "Done!"


echo "Registering Foxes and Rabbits lambda..."

aws lambda create-function \
	--function-name simulate-lambda \
	--zip-file fileb://../res/"Foxes and Rabbits"/target/foxrabbit-1.0.0-SNAPSHOT-jar-with-dependencies.jar \
	--handler pt.ulisboa.tecnico.cnv.foxrabbit.SimulationHandler \
	--runtime java11 \
	--timeout 30 \
	--memory-size 512 \
	--role arn:aws:iam::${AWS_ACCOUNT_ID}:role/lambda-role > /dev/null

echo "Done!"


echo "Registering Insect wars lambda..."

aws lambda create-function \
	--function-name insectwar-lambda \
	--zip-file fileb://../res/"Insect Wars"/target/insectwar-1.0.0-SNAPSHOT-jar-with-dependencies.jar \
	--handler pt.ulisboa.tecnico.cnv.insectwar.WarSimulationHandler \
	--runtime java11 \
	--timeout 300 \
	--memory-size 512 \
	--role arn:aws:iam::${AWS_ACCOUNT_ID}:role/lambda-role > /dev/null

echo "Done!"
#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)"
source $SCRIPT_DIR/config.sh

aws iam create-role \
	--role-name lambda-role \
	--assume-role-policy-document '{"Version": "2012-10-17","Statement": [{ "Effect": "Allow", "Principal": {"Service": "lambda.amazonaws.com"}, "Action": "sts:AssumeRole"}]}'

sleep 5

aws iam attach-role-policy \
	--role-name lambda-role \
	--policy-arn arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole

sleep 5

aws lambda create-function \
	--function-name image-compression-lambda \
	--zip-file fileb://../res/"Image Compression"/target/compression-1.0.0-SNAPSHOT-jar-with-dependencies.jar \
	--handler pt.ulisboa.tecnico.cnv.compression.BaseCompressingHandler \
	--runtime java11 \
	--timeout 5 \
	--memory-size 512 \
	--role arn:aws:iam::$AWS_ACCOUNT_ID:role/lambda-role

sleep 5

aws lambda create-function \
	--function-name foxes-and-rabbits-lambda \
	--zip-file fileb://../res/"Foxes and Rabbits"/target/foxrabbit-1.0.0-SNAPSHOT-jar-with-dependencies.jar \
	--handler pt.ulisboa.tecnico.cnv.foxrabbit.SimulationHandler \
	--runtime java11 \
	--timeout 5 \
	--memory-size 512 \
	--role arn:aws:iam::$AWS_ACCOUNT_ID:role/lambda-role

sleep 5

aws lambda create-function \
	--function-name insect-wars-lambda \
	--zip-file fileb://../res/"Insect Wars"/target/insectwar-1.0.0-SNAPSHOT-jar-with-dependencies.jar \
	--handler pt.ulisboa.tecnico.cnv.insectwar.WarSimulationHandler \
	--runtime java11 \
	--timeout 5 \
	--memory-size 512 \
	--role arn:aws:iam::$AWS_ACCOUNT_ID:role/lambda-role
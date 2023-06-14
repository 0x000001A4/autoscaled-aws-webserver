#!/bin/bash

# Syntax:  ./testfoxrabbit.sh <generations> <world> <scenario>
# Example: ./testfoxrabbit.sh 1000 4 2

function print_usage {
    echo "Usage: ${BASH_SOURCE[0]} <generations> <world> <scenario>"
}

if [ $# -ne 3 ]; then
    print_usage
    exit
fi

if [ ! -f instance.dns ]; then
    echo "File instance.dns not found!"
    exit
fi

HOST=$(cat instance.dns)
PORT=8000
GENERATIONS=$1
WORLD=$2
SCENARIO=$3

echo "Testing ${HOST}:${PORT} with ${GENERATIONS} generations on world ${WORLD}, scenario ${SCENARIO}..."
echo

function test_batch_requests {
	REQUESTS=3
	CONNECTIONS=1
	ab -n $REQUESTS -c $CONNECTIONS $HOST:$PORT/simulate\?generations=$GENERATIONS\&world=$WORLD\&scenario=$SCENARIO
}

function test_single_requests {

	curl $HOST:$PORT/simulate\?generations=$GENERATIONS\&world=$WORLD\&scenario=$SCENARIO
}

test_single_requests
test_batch_requests

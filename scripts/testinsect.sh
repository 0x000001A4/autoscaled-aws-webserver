#!/bin/bash

# Syntax:  ./testinsect.sh  <max> <army1> <army2>
# Example: ./testinsect.sh 1000 10 10

function print_usage {
    echo "Usage: ${BASH_SOURCE[0]} <max> <army1> <army2>"
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
max=$1
army1=$2
army2=$3

echo "Testing ${HOST}:${PORT} with ${max} rounds and armies size of ${army1} and ${army2}..."
echo

function test_batch_requests {
	REQUESTS=3
	CONNECTIONS=1
	ab -n $REQUESTS -c $CONNECTIONS $HOST:$PORT/insectwar\?max=$max\&army1=$army1\&army2=$army2
}

function test_single_requests {

	curl $HOST:$PORT/insectwar\?max=$max\&army1=$army1\&army2=$army2
}

test_single_requests
test_batch_requests

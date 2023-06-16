#!/bin/bash

# Syntax:  ./testcompress.sh <input image> <compression-factor>
# Example: ./testcompress.sh res/Ct0zUz-XgAAV69z.jpg 0.1

function print_usage {
    echo "Usage: ${BASH_SOURCE[0]} <input image> <compression-factor>"
}

if [ $# -ne 2 ]; then
    print_usage
    exit
fi

if [ ! -f instance.dns ]; then
    echo "File instance.dns not found!"
    exit
fi

HOST=$(cat instance.dns)
PORT=8000
INPUT=$1
COMPRESSION_FACTOR=$2

TARGET_FORMAT=$(file --extension ${INPUT} | awk '{ print $2; }' | awk -F '/' '{ print $1; }')

echo "Testing ${HOST}:${PORT} with image ${INPUT} (format: ${TARGET_FORMAT}) and compression factor ${COMPRESSION_FACTOR}..."
echo

function test_batch_requests {
	REQUESTS=3
	CONNECTIONS=1
	echo "targetFormat:$TARGET_FORMAT;compressionFactor:$COMPRESSION_FACTOR;data:image/jpeg;base64,$(base64 --wrap=0 $INPUT)" > /tmp/image.body
	ab -n $REQUESTS -c $CONNECTIONS -p /tmp/image.body "$HOST:$PORT/compressimage"
}

function test_single_requests {
	BODY=$(base64 --wrap=0 $INPUT)
	BODY=$(echo "targetFormat:$TARGET_FORMAT;compressionFactor:$COMPRESSION_FACTOR;data:image/jpeg;base64,$BODY")

	curl -s -d $BODY $HOST:$PORT/compressimage #-o /tmp/$TARGET_FORMAT-image.dat
	exit

	OUTPUT=$(cat /tmp/$TARGET_FORMAT-image.dat)   # read raw output
	OUTPUT=${OUTPUT#*,}                           # parse output after comma
	echo $OUTPUT > /tmp/$TARGET_FORMAT-image.dat  # write pure Base64 output to a file
	cat /tmp/$TARGET_FORMAT-image.dat | base64 -d > /tmp/$TARGET_FORMAT-image.$TARGET_FORMAT
}

test_single_requests
exit
test_batch_requests

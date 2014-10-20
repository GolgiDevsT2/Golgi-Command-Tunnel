#!/bin/sh

if [ "$GCT_NAME" = "" ]; then
    echo "You must specify an endpoint name in the GCT_NAME environmant variable"
    exit -1
fi

if [ $# -ne 3 ]; then
	echo "Usage: $0 <server-identity> <remote-filename> <local-filename>";
	exit -1
fi

TARGET=$1
REMOTE=$2
LOCAL=$3

java -classpath gct.jar:$HOME/Golgi-Pkg/LATEST//common/golgi_j2se.jar:libs/log4j-1.2.17.jar:libs/httpclient-4.3.3.jar io.golgi.gct.Client -devKey `cat ../Golgi.DevKey` -appKey `cat ../Golgi.AppKey` -identity $GCT_NAME -target $TARGET -fgetSrc "$REMOTE" -fgetDst "$LOCAL"

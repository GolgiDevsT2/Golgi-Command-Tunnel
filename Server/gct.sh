#!/bin/sh

if [ $# -lt 2 ]; then
	echo "Usage: $0 <server-identity> <cmd> [arg1 [arg2...]]";
	exit -1
fi

TARGET=$1
shift

java -classpath gct.jar:$HOME/Golgi-Pkg/LATEST//common/golgi_j2se.jar:libs/log4j-1.2.17.jar:libs/httpclient-4.3.3.jar io.golgi.gct.Client -devKey `cat ../Golgi.DevKey` -appKey `cat ../Golgi.AppKey` -identity `hostname`-CLI -target $TARGET -- $*

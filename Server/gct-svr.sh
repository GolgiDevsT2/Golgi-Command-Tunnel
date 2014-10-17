#!/bin/sh

if [ $# != 1 ]; then
	echo "Usage: $0 <identity>"
	exit -1
fi

java -classpath gct.jar:$HOME/Golgi-Pkg/LATEST//common/golgi_j2se.jar:libs/log4j-1.2.17.jar:libs/httpclient-4.3.3.jar io.golgi.gct.Server -devKey `cat ../Golgi.DevKey` -appKey `cat ../Golgi.AppKey` -identity  $1

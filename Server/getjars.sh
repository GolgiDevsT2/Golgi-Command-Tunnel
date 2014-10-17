#!/bin/sh

/bin/echo -n "Downloading Needed Apache JARs: "
rm -rf libs;
mkdir -p libs; 
(cd libs;
    curl -s http://ftp.heanet.ie/mirrors/www.apache.org/dist//httpcomponents/httpclient/binary/httpcomponents-client-4.3.3-bin.tar.gz | tar -xzf -;
    curl -s http://ftp.heanet.ie/mirrors/www.apache.org/dist/logging/log4j/1.2.17/log4j-1.2.17.tar.gz | tar -xzf - 
    find . -name httpclient-4.3.3.jar -print | (while read j; do cp -f $j .; done)
    find . -name log4j-1.2.17.jar | (while read j; do cp -f $j .; done)

    find . -type d -maxdepth 1 | grep / | (while read d; do rm -rf $d; done)

)
echo "DONE"

    
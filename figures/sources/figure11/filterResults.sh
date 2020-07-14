#!/bin/bash

RESULTFILE="../Results.csv"

CONFIG="b-staggered"
RADIUS="0.04 0.063 0.08"
TARGET="B"

while 
	read cCONFIG CONFIG <<EOF
$CONFIG
EOF
	read cTARGET TARGET <<EOF
$TARGET
EOF
	[ -n "$cCONFIG" ]
do
		echo "$cCONFIG $cTARGET"
		TEMP="temp.csv"
		head -n 1 $RESULTFILE > $TEMP
		grep "$cCONFIG" $RESULTFILE >> $TEMP
		sed -i '/\#NV/d' $TEMP
	for cRADIUS in $RADIUS; do
		OUTPUT="resultsCase${cTARGET}-${cRADIUS}.csv"
	    	head -n 1 $TEMP | cut -d';' -f1,7,8,5,11 > $OUTPUT
			grep ";$cRADIUS;" $TEMP | egrep ";0.01;" | cut -d';' -f1,7,8,5,11 >> $OUTPUT 
	done
	rm $TEMP
	
done

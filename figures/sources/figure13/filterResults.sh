#!/bin/bash

RESULTFILE="../Results.csv"

CONFIG="a-line b-staggered c-align d-rectangle"
RADIUS="0.04 0.063 0.08"
TARGET="A B C D"

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
	    	head -n 1 $TEMP | cut -d';' -f1,7,8,5,18,36 > $OUTPUT
	    	grep ";$cRADIUS;" $TEMP | cut -d';' -f1,7,8,5,18,36 >> $OUTPUT 
	done
	rm $TEMP
	
done

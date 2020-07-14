#!/bin/bash

RESULTFILE="Results.csv"

MATCH="a-line b-staggered c-align d-rectangle"
TARGET="A B C D"

COLUMNS="1,18,23,8,7"

while 
	read cMATCH MATCH <<EOF
$MATCH
EOF
	read cTARGET TARGET <<EOF
$TARGET
EOF
	[ -n "$cMATCH" ]
do
	echo "$cMATCH $cTARGET"
	OUTPUT="resultsCase${cTARGET}.csv"
	TEMP="temp.csv"
	head -n 1 $RESULTFILE > $TEMP
	grep "$cMATCH" $RESULTFILE >> $TEMP
	sed -i 's/\#NV//g' $TEMP
	cut -d';' -f1,7,8,18,23 $TEMP > $OUTPUT
	rm $TEMP
	
done

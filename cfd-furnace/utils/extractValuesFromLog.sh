#!/bin/bash


DELIMITER='\t'

LOGFILE=$1

EXTRACTFILE="${LOGFILE%*.log}_extracted.csv"



HEADER=$(grep '[[:space:]]Iteration' $LOGFILE | tail -1 |tr -s " ")
# Strip leading whitespace
HEADER="${HEADER# }"

# Strip units
HEADER="$(echo "$HEADER" |  sed 's/([a-zA-Z\/]*)//g' | tr -s " ")"





LOGVALUES=$(grep '[[:space:]]10000' $LOGFILE | tr -s " ")


if [ -z "$LOGVALUES" ]; then
	echo "Iteration 10000 not found"
	LOGVALUES=$(tail -n 160 $LOGFILE | head -n1 | tr -s " ")
	
	if [ -z "$LOGVALUES" ]; then
		echo "Log file not as expected. Looking for last Log header"
		LOGVALUES=$(grep -A 1 '[[:space:]]Iteration' $LOGFILE | tail -1 | tr -s " ")
		echo "Using iteration $(echo "$LOGVALUES" | cut -d -f2)"
	fi
else
	echo "Simulation ran till iteration 10000."
fi

LOGVALUES="${LOGVALUES# }"

if [ -z "$LOGVALUES" ]; then
	echo "Failed to recover log's values."
else
	echo "Writing log's values to ${EXTRACTFILE}"

	## Writing data to file, inserting delimiter.
	echo "$HEADER" | tr ' ' $DELIMITER > $EXTRACTFILE
	echo "$LOGVALUES" | tr ' ' $DELIMITER >> $EXTRACTFILE

	echo "Done."
fi

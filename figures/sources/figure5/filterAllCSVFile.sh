#!/bin/bash


SOURCEFILE=SMR_C_All_Export.csv
EXPORTBASEFILENAME=SMR_C_TubeRad_
while read LINE; do
	tubeRad=$(echo $LINE | tr -d ' ' | tr -d '#' | cut -d',' -f5 ) 
	tubeRad=${tubeRad//\./}
	
	EXPORTTARGET=${EXPORTBASEFILENAME}${tubeRad}.csv

	if [ ! -e $EXPORTTARGET ]; then
		head -n 1 $SOURCEFILE | tr -d '#' > $EXPORTTARGET
	fi
	
	echo $LINE >> $EXPORTTARGET

done < $SOURCEFILE

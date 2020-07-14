#!/bin/bash
## Reading constructed pipe distribution files (csv) and outputs individuals

SOURCE=$1
CURRENTDIR="."
CASESETUPINFODIR="$CURRENTDIR/case-setup-info"


IDFILE="IDs.csv"
IDSINK="IDs"

[ -d $IDSINK ] || mkdir $IDSINK


IDFILEHEADER="ID,Source,Tubes,MassFlowCoefficient,DistanceFactor,Radius,Location"


## Create IDFILE if not already present and not empty
if [ ! -f "$IDFILE" ]; then
	echo "$IDFILE not found. creating it."
	echo "$IDFILEHEADER" > $IDFILE
fi
 
## Present IDs
IDCOUNT=$(($(cat $IDFILE | wc -l) -1))
echo "There have been $IDCOUNT individuals found in the log"



## Parsing source files
for RADII in $(cat $CASESETUPINFODIR/radii.csv); do
	for SOURCE in $(cat $CASESETUPINFODIR/cases.csv); do
		for MASSFLOW in $(cat $CASESETUPINFODIR/perTubeFlowRates.csv); do
			LINECOUNT=0
			while IFS='' read -r line || [[ -n "$line" ]]; do
				IDCOUNT=$((IDCOUNT+1))
				LINECOUNT=$((LINECOUNT+1))
				TUBECOUNT=$(( $(echo $line|awk -F',' '{print NF}') / 2 )) 
				IDNAME="ID${IDCOUNT}"
				IDLOCATION="${CURRENTDIR}/${IDSINK}/${IDNAME}"
		
				
				DISTANCEFACTOR=$(sed -n ${LINECOUNT}p  $CASESETUPINFODIR/distanceFactor.csv)
			
				echo "Processing $IDNAME"
			
				mkdir $IDLOCATION

				cp $CASESETUPINFODIR/reactor.java $IDLOCATION/
				cp $CASESETUPINFODIR/run.sh $IDLOCATION/
				
				touch ${IDLOCATION}/pipes.csv
				
			
				#writing indiv file
				for i in $(seq 1 $TUBECOUNT); do
					XFIELD=$(( ( $i -1 ) *2 + 1))
					YFIELD=$((XFIELD + 1))
			
					XVAL=$(echo $line|cut -d',' -f$XFIELD)
					YVAL=$(echo $line|cut -d',' -f$YFIELD)
			
					echo "${XVAL},${YVAL}" >> ${IDLOCATION}/pipes.csv
				done 
				#writing mass flow rate
				echo "$MASSFLOW" > ${IDLOCATION}/massflow.input
				echo "$RADII" > ${IDLOCATION}/radius.input
				
			
				echo "$IDNAME,${SOURCE:0:-4},${TUBECOUNT},$MASSFLOW,$DISTANCEFACTOR,$RADII,${CURRENTDIR}/${IDSINK}/${IDNAME}" >> $IDFILE
				
				
			
			
			done < "$CASESETUPINFODIR/$SOURCE"
		done
	done
done


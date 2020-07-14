#!/bin/bash




RESULTSTEM="results/output"

## Assorted Data
echo -e "ID\tValid\tCase\tMassFlowPerTube\tInnerRadius(m)\tPressureDrop(Pa)\tOutletTemperature(K)\tHeatTransferBalance_Inlet/Outlet(W)\tHeatTransferPerAreaMean(W/m2)\tHeatTransferPerAreaSTD(W/m2)\tHeatTransferPerAreaVAR(W/m2)\tHeatTransferMean(W)\tHeatTransferSTD(W)\tHeatTransferVAR(W)\tContinuity\tSolvingStatus" > $RESULTSTEM-temp.csv
for id in IDs/*; do
	((i=i%4)); ((i++==0)) && wait
	(
		echo -e "collecting ${id}"
		VALIDITYCOUNT=0
		NEWESTLOG=$(ls --color=never -t $id/*.log | head -n1)
		GREPCOUNT="$(grep -c ERROR ${NEWESTLOG:=error.txt})"
		(( VALIDITYCOUNT+=${GREPCOUNT} ))
		
		if [ ! -f $id/rep_Pressure_Drop.val ]; then (( VALIDITYCOUNT+=1 )); fi
	        RESULTSTRING="${id:6}"
		if [ $VALIDITYCOUNT == 0 ] ; then 
			RESULTSTRING="$RESULTSTRING\t1"
		else
			RESULTSTRING="$RESULTSTRING\t-$VALIDITYCOUNT"
		fi
		RESULTSTRING="$RESULTSTRING\t$(sed -n ${id:6}p IDs.csv | cut -d',' -f2)" 
		RESULTSTRING="$RESULTSTRING\t$(cat $id/massflow.input)"
		RESULTSTRING="$RESULTSTRING\t$(cat $id/radius.input)"
		RESULTSTRING="$RESULTSTRING\t$(cat $id/rep_Pressure_Drop.val)"
		RESULTSTRING="$RESULTSTRING\t$(cat $id/rep_Temperature_Outlet.val)"
		RESULTSTRING="$RESULTSTRING\t$(cat $id/rep_Heat_Transfer_Balance_InletOutlet.val)"
		RESULTSTRING="$RESULTSTRING\t$(cat $id/rep_Heat_Transfer_AreaAveraged_Wm2_mean.val)"
		RESULTSTRING="$RESULTSTRING\t$(cat $id/rep_Heat_Transfer_AreaAveraged_Wm2_std.val)"
		RESULTSTRING="$RESULTSTRING\t$(cat $id/rep_Heat_Transfer_AreaAveraged_Wm2_variance.val)"
		RESULTSTRING="$RESULTSTRING\t$(cat $id/rep_Heat_Transfer_W_mean.val)"
		RESULTSTRING="$RESULTSTRING\t$(cat $id/rep_Heat_Transfer_W_std.val)"
		RESULTSTRING="$RESULTSTRING\t$(cat $id/rep_Heat_Transfer_W_variance.val)"
               	HEADERCOUNT="$(grep -c "Iteration  " ${NEWESTLOG})"

                if [ $HEADERCOUNT -gt 0 ]; then
                        CONTINUITY=$(sed -n 'H; /Iteration     Continuity/h; ${g;p;}' $NEWESTLOG | head -n 2 | tail -n1 | sed 's/\s\s*/ /g' | cut -d' ' -f3)
                        if [ $(awk 'BEGIN{print ('$CONTINUITY' > '1e-1')?0:1}') -eq 1 ]; then
				RESULTSTRING="$RESULTSTRING\t$CONTINUITY\tconverged"
			else
				RESULTSTRING="$RESULTSTRING\t$CONTINUITY\tdiverged"
                        fi
                else	
			RESULTSTRING="$RESULTSTRING\t-1\tcouldnt filter residuals"
		fi


			
		echo -e $RESULTSTRING | sed 's///g' >> $RESULTSTEM-temp.csv
	) &
done
wait
echo -e "Assorted pre-collection done"
cat $RESULTSTEM-temp.csv | sort -n -t$'\t' -k1,1n >$RESULTSTEM-assorted.csv
echo -e "Assorted Data collected"

## Temperature
echo -e "ID,Temperatures(K)" > $RESULTSTEM-temp.csv
for id in IDs/*; do
	((i=i%4)); ((i++==0)) && wait
	(
		RESULTSTRING="${id:6}"
		for val in $id/rep_Temperature_PWALL_*.val; do
			RESULTSTRING="$RESULTSTRING\t$(cat $val)"
		done
		echo -e "$RESULTSTRING"  | sed 's/^M//g' >> $RESULTSTEM-temp.csv
	) &
done
wait
cat $RESULTSTEM-temp.csv | sort -n -t$'\t' -k1,1n >$RESULTSTEM-temperatures.csv
echo -e "Individual Temperatures collected"

## Heat Transfer Area Averaged
echo -e "ID,HeatTransferPerArea(W/m2)" > $RESULTSTEM-temp.csv
for id in IDs/*; do
        ((i=i%4)); ((i++==0)) && wait
        (
		RESULTSTRING="${id:6}"
		for val in $id/rep_Heat_Transfer_AreaAveraged_Wm2_PWALL_*.val; do
			RESULTSTRING="$RESULTSTRING\t$(cat $val)"
		done
		echo -e "$RESULTSTRING" | sed 's/^M//g' >> $RESULTSTEM-temp.csv
	) &
done
wait
cat $RESULTSTEM-temp.csv | sort -n -t$'\t' -k1,1n >$RESULTSTEM-heatTransferPerArea.csv


## Heat Transfer
echo -e "ID,HeatTransfer(W)" > $RESULTSTEM-temp.csv
for id in IDs/*; do
        ((i=i%4)); ((i++==0)) && wait
        (
		RESULTSTRING="${id:6}"
		for val in $id/rep_Heat_Transfer_W_PWALL_*.val; do
			RESULTSTRING="$RESULTSTRING\t$(cat $val)"
		done
		echo -e "$RESULTSTRING" | sed 's/^M//g' >> $RESULTSTEM-temp.csv
	) &
done
wait
cat $RESULTSTEM-temp.csv | sort -n -t$'\t' -k1,1n >$RESULTSTEM-heatTransfer.csv

## Heat Transfer
echo -e "ID,HeatTransferByRadiation(W)" > $RESULTSTEM-temp.csv
for id in IDs/*; do
        ((i=i%4)); ((i++==0)) && wait
        (
		RESULTSTRING="${id:6}"
		for val in $id/rep_Heat_Flux_Radiation_PWALL_*.val; do
			RESULTSTRING="$RESULTSTRING\t$(cat $val)"
		done
		echo -e "$RESULTSTRING" | sed 's/^M//g' >> $RESULTSTEM-temp.csv
	) &
done
wait
cat $RESULTSTEM-temp.csv | sort -n -t$'\t' -k1,1n >$RESULTSTEM-heatFluxRadiation.csv
## Heat Flux through Conduction
echo -e "ID,HeatTransferByConduction(W)" > $RESULTSTEM-temp.csv
for id in IDs/*; do
        ((i=i%4)); ((i++==0)) && wait
        (
		RESULTSTRING="${id:6}"
		for val in $id/rep_Heat_Flux_Conduction_PWALL_*.val; do
			RESULTSTRING="$RESULTSTRING\t$(cat $val)"
		done
		echo -e "$RESULTSTRING" | sed 's/^M//g' >> $RESULTSTEM-temp.csv
	) &
done
cat $RESULTSTEM-temp.csv | sort -n -t$'\t' -k1,1n > $RESULTSTEM-heatFluxConduction.csv
echo -e "Heat Transfer data collected"


##Force
echo -e "ID,Force(N)" > $RESULTSTEM-temp.csv
for id in IDs/*; do
        ((i=i%4)); ((i++==0)) && wait
        (
		RESULTSTRING="${id:6}"
		for val in $id/rep_Force_PWALL_*.val; do
			RESULTSTRING="$RESULTSTRING\t$(cat $val)"
		done
		echo -e "$RESULTSTRING" >> $RESULTSTEM-temp.csv
	) &
done
wait
cat $RESULTSTEM-temp.csv | sort -n -t$'\t' -k1,1n >$RESULTSTEM-force.csv


## Clean up.
rm $RESULTSTEM-temp.csv
echo -e "done."

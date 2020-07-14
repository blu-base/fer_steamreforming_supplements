#!/bin/bash




RESULTSTEM="results/output"

## Assorted Data
echo "Mesh,Massflow,Case,Valid,BaseSize,MassFlowPerTube,PressureDrop(Pa),OutletTemperature(K),HeatTransferBalance_Inlet/Outlet(W),HeatTransferPerAreaMean(W/m2),HeatTransferPerAreaSTD(W/m2),HeatTransferPerAreaVAR(W/m2),HeatTransferMean(W),HeatTransferSTD(W),HeatTransferVAR(W)" > $RESULTSTEM-temp.csv
for mflow in mflow*; do
	for style in $mflow/style*; do
		for mesh in $style/m*; do
			VALIDITYCOUNT=0
		
			GREPCOUNT="$(grep -c ERROR $(ls --color=never -t $mesh/*.log | head -n1))"
			(( VALIDITYCOUNT+=${GREPCOUNT} ))
			
			if [ ! -f $mesh/rep_Pressure_Drop.val ]; then (( VALIDITYCOUNT+=1 )); fi
		
		        RESULTSTRING="${mesh##*/m},$mflow,${style##*/}"
			if [ $VALIDITYCOUNT == 0 ] ; then 
				RESULTSTRING="$RESULTSTRING,1"
			else
				RESULTSTRING="$RESULTSTRING,-$VALIDITYCOUNT"
			fi
			RESULTSTRING="$RESULTSTRING,$(cat $mesh/basesize.input)"
			RESULTSTRING="$RESULTSTRING,$(cat $mesh/massflow.input)"
			RESULTSTRING="$RESULTSTRING,$(cat $mesh/rep_Pressure_Drop.val)"
			RESULTSTRING="$RESULTSTRING,$(cat $mesh/rep_Temperature_Outlet.val)"
			RESULTSTRING="$RESULTSTRING,$(cat $mesh/rep_Heat_Transfer_Balance_InletOutlet.val)"
			RESULTSTRING="$RESULTSTRING,$(cat $mesh/rep_Heat_Transfer_AreaAveraged_Wm2_mean.val)"
			RESULTSTRING="$RESULTSTRING,$(cat $mesh/rep_Heat_Transfer_AreaAveraged_Wm2_std.val)"
			RESULTSTRING="$RESULTSTRING,$(cat $mesh/rep_Heat_Transfer_AreaAveraged_Wm2_variance.val)"
			RESULTSTRING="$RESULTSTRING,$(cat $mesh/rep_Heat_Transfer_W_mean.val)"
			RESULTSTRING="$RESULTSTRING,$(cat $mesh/rep_Heat_Transfer_W_std.val)"
			RESULTSTRING="$RESULTSTRING,$(cat $mesh/rep_Heat_Transfer_W_variance.val)"
			
			echo $RESULTSTRING >> $RESULTSTEM-temp.csv
		done
	done
done
cat $RESULTSTEM-temp.csv | sort -t',' -k2,2 -k1,1n >$RESULTSTEM-assorted.csv
echo "Assorted Data collected"

## Temperature
echo "Mesh,Massflow,Case,Temperatures(K)" > $RESULTSTEM-temp.csv
for mflow in mflow*; do
        for style in $mflow/style*; do
                for mesh in $style/m*; do
			RESULTSTRING="${mesh##*/m},$mflow,${style##*/}"
			for val in $mesh/rep_Temperature_PWALL_*.val; do
				RESULTSTRING="$RESULTSTRING,$(cat $val)"
			done
			echo "$RESULTSTRING" >> $RESULTSTEM-temp.csv
		done
	done
done
cat $RESULTSTEM-temp.csv | sort -t"," -k2,2 -k1,1n >$RESULTSTEM-temperatures.csv
echo "Individual Temperatures collected"

## Heat Transfer Area Averaged
echo "Mesh,Massflow,Case,HeatTransferPerArea(W/m2)" > $RESULTSTEM-temp.csv
for mflow in mflow*; do
        for style in $mflow/style*; do
                for mesh in $style/m*; do
                        RESULTSTRING="${mesh##*/m},$mflow,${style##*/}"
			for val in $mesh/rep_Heat_Transfer_AreaAveraged_Wm2_PWALL_*.val; do
				RESULTSTRING="$RESULTSTRING,$(cat $val)"
			done
			echo "$RESULTSTRING" >> $RESULTSTEM-temp.csv
		done
	done
done
cat $RESULTSTEM-temp.csv | sort -t"," -k2,2 -k1,1n >$RESULTSTEM-heatTransferPerArea.csv


## Heat Transfer
echo "Mesh,Massflow,Case,HeatTransfer(W)" > $RESULTSTEM-temp.csv
for mflow in mflow*; do
        for style in $mflow/style*; do
                for mesh in $style/m*; do
                        RESULTSTRING="${mesh##*/m},$mflow,${style##*/}"
			for val in $mesh/rep_Heat_Transfer_W_PWALL_*.val; do
				RESULTSTRING="$RESULTSTRING,$(cat $val)"
			done
			echo "$RESULTSTRING" >> $RESULTSTEM-temp.csv
		done
	done
done
cat $RESULTSTEM-temp.csv | sort -t"," -k2,2 -k1,1n >$RESULTSTEM-heatTransfer.csv

## Heat Transfer
echo "Mesh,Massflow,Case,HeatTransferByRadiation(W)" > $RESULTSTEM-temp.csv
for mflow in mflow*; do
        for style in $mflow/style*; do
                for mesh in $style/m*; do
                        RESULTSTRING="${mesh##*/m},$mflow,${style##*/}"
			for val in $mesh/rep_Heat_Flux_Radiation_PWALL_*.val; do
				RESULTSTRING="$RESULTSTRING,$(cat $val)"
			done
			echo "$RESULTSTRING" >> $RESULTSTEM-temp.csv
		done
	done
done
cat $RESULTSTEM-temp.csv | sort -t"," -k2,2 -k1,1n >$RESULTSTEM-heatFluxRadiation.csv
## Heat Flux through Conduction
echo "Mesh,Massflow,Case,HeatTransferByConduction(W)" > $RESULTSTEM-temp.csv
for mflow in mflow*; do
        for style in $mflow/style*; do
                for mesh in $style/m*; do
                        RESULTSTRING="${mesh##*/m},$mflow,${style##*/}"
			for val in $mesh/rep_Heat_Flux_Conduction_PWALL_*.val; do
				RESULTSTRING="$RESULTSTRING,$(cat $val)"
			done
			echo "$RESULTSTRING" >> $RESULTSTEM-temp.csv
		done
	done
done
cat $RESULTSTEM-temp.csv | sort -t"," -k2,2 -k1,1n > $RESULTSTEM-heatFluxConduction.csv
echo "Heat Transfer data collected"


##Force
echo "Mesh,Massflow,Case,Force(N)" > $RESULTSTEM-temp.csv
for mflow in mflow*; do
        for style in $mflow/style*; do
                for mesh in $style/m*; do
                        RESULTSTRING="${mesh##*/m},$mflow,${style##*/}"
			for val in $mesh/rep_Force_PWALL_*.val; do
				RESULTSTRING="$RESULTSTRING,$(cat $val)"
			done
			echo "$RESULTSTRING" >> $RESULTSTEM-temp.csv
		done
	done
done
cat $RESULTSTEM-temp.csv | sort -t"," -k2,2 -k1,1n >$RESULTSTEM-force.csv


## Clean up.
rm $RESULTSTEM-temp.csv
echo "done."

#!/bin/bash


bash filterResults.sh

for f in resultsCase*; do
rm -rf temp 2>/dev/null
	while read -r line; do 
		lID=$(echo $line | cut -d';' -f1)
		stdev=$(egrep "^${lID};" tempSTD.csv | cut -d';' -f 2)
		
		echo "$line;$stdev" >> temp
	done < $f 
	mv temp $f
done
rm -rf temp 2>/dev/null

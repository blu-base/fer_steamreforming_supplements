#!bin/bash

SOURCE="filtered.csv"


i=1
while read -r line 
do
	charlength=$(echo "$line" | cut -d';' -f5)
	heatfluxratio=$(echo "$line" | cut -d';' -f26)
	meanheatflux=$(echo "$line" | cut -d';' -f18)
	aveviewfactor=$(echo "$line" | cut -d';' -f34)

	printf "\\\\node (n%sc) [right=0.25cm of n%sb,align=left] {" "$i" "$i"
	printf '\\renewcommand\\arraystretch{0.8}\setlength{\\tabcolsep}{1pt}{\scalebox{0.6}{\\begin{tabular}{ll}'
	printf "$\gls{charLength}/D$&$=%.2f$ \\\\\\\\ " "$charlength"
	printf "$\gls{heatfluxratio}$&$=%s\\%%$ \\\\\\\\ " "${heatfluxratio:0:-1}"
	printf "$\gls{heatfluxmean}$&$=%s\si{\watt}$ \\\\\\\\ " "${meanheatflux}"
	printf "$\gls{viewfactormean}$&$=%.2f$ \\\\\\\\ " "${aveviewfactor}"
	printf '\\end{tabular}}}'
	printf "};\n"

	i=$((i + 1))

done < <(tail -n+2 $SOURCE)

printf "\n"

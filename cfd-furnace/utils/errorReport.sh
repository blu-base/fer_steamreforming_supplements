#!/bin/bash


OUTPUTFILE="pdf/errorReport.tex"





read -d '' DOCHEADER << EOF
\\\\documentclass{article}
\\\\usepackage[top=1cm,left=1cm,right=1cm,bottom=1cm]{geometry}
\\\\usepackage{graphicx}


%\\\\newcommand{\\\\picInc}[2]{\\\\includegraphics[width=5cm]{../images/#1/#2_#1.jpg} }
\\\\newcommand{\\\\pic}[2]{\\\\includegraphics[width=0.31\\\\linewidth]{../IDs/{#1}/{#2}.jpg}}

\\\\begin{document}
	
	
	\\\\tableofcontents
	\\\\clearpage
	
\\\\section{Reports}
\\\\clearpage
EOF

read -d '' DOCFOOTER << EOF
\\\\end{document}
EOF

read -d '' PAGETEMPLATEHEAD <<EOF
%%%% ------------------------------------------------------------------------------------------------
%%%%  A new Info Page
\\\\subsection{IDNUMBER}
%%%% ------------------------------------------------------------------------------------------------
\\\\centering
\\\\begin{tabular}{ccc}
	Velocity & Pressure & Temperature \\\\\\\\
	\\\\pic{IDNUMBER}{scn_Velocity} & \\\\pic{IDNUMBER}{scn_Pressure} &	\\\\pic{IDNUMBER}{scn_Temperature} \\\\\\\\
	Vorticity & Mesh & Residuals \\\\\\\\
	\\\\pic{IDNUMBER}{scn_Geometry} & \\\\pic{IDNUMBER}{scn_Mesh} & \\\\pic{IDNUMBER}{plt_Residuals} \\\\\\\\
\\\\end{tabular}
\\\\begin{flushleft}
	\\\\Large OCCURANCES Errors found
\\\\end{flushleft}
{\\\\tiny 
\\\\begin{verbatim}
EOF

read -d '' PAGETEMPLATEFOOT << EOF
\\\\end{verbatim}
}
\\\\clearpage

EOF




#echo "$PAGETEMPLATE" | sed "s/IDNUMBER/ID123/g"


echo "$DOCHEADER" > $OUTPUTFILE



## Creating Error Report from all IDs
for id in IDs/*; do
		echo -e "destilling ${id}"
		VALIDITYCOUNT=0
		NEWESTLOG=$(ls --color=never -t $id/*.log | head -n1)
		GREPCOUNT="$(grep -c ERROR ${NEWESTLOG:=error.txt})"
		(( VALIDITYCOUNT+=${GREPCOUNT} ))

		if [ ! $VALIDITYCOUNT == 0 ] ; then 
			echo -e "\treporting $id"
			echo "$PAGETEMPLATEHEAD" | sed "s/IDNUMBER/${id:4}/g" | sed "s/OCCURANCES/$GREPCOUNT/g" >> $OUTPUTFILE
			grep -A15 -B15 ERROR ${NEWESTLOG:=error.txt} >> $OUTPUTFILE
			echo "$PAGETEMPLATEFOOT" >> $OUTPUTFILE
		fi
done
wait



echo "$DOCFOOTER" >> $OUTPUTFILE


echo -e "done."

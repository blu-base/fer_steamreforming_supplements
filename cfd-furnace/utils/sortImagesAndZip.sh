#!/bin/bash
for f in $(cat case-setup-info/imageList.csv); do
	((i=i%6)); ((i++==0)) && wait
	(
		TARGETFOLDER=images/${f:0:${#f}-4}
		mkdir -p $TARGETFOLDER
				
		for d in IDs/ID*; do
				cp $d/$f $TARGETFOLDER/${d:4}_$f
		done
		
		tar -czvf ${f:0:${#f}-4}.tar.gz $TARGETFOLDER/*
	) &
done

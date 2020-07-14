#!/bin/bash

MATCH="0.04 0.063 0.08"
TARGET="A B C D"


for t in $TARGET; do
  RESFILE=resultsCase${t}
  for m in $MATCH; do
    OUTPUT=$RESFILE-$m.csv
    head -n 1 $RESFILE.csv > $OUTPUT
    grep ";$m;" $RESFILE.csv >> $OUTPUT
  done
done

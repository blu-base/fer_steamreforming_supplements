#!/bin/bash

SOURCE="../Results.csv"
OUTPUT="filtered.csv"

head -n 1 $SOURCE > $OUTPUT
for i in {13..24}; do
	grep "^3${i};" $SOURCE >> $OUTPUT
done


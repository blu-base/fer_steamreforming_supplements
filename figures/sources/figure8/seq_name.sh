#!/bin/bash

j=1
for i in {13..24}; do
	mv ID3${i}_*.jpg temp_${j}.jpg
	j=$((j +1))
done

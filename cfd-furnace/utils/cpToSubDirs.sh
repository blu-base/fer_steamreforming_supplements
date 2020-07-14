#!/bin/bash

for d in IDs/*; do 
	cp ${@} $d
done

#!/bin/bash

for d in ./*/; do echo ${d#*/}; cd ${d#*/}; pwd; bash filterResults.sh; bash filterResults2.sh; cd ..; done

#!/bin/bash
# This file is simple running script for the case of this directory.
# In the current form, it runs locally with the given number of processors.
# Under normal conditions, you would use the work load manager and
# distribute running of all cases.

macro="reactor.java"
numberprocessors="6"
mypodkey="<yourkey>"


# Set up your environment to add "starccm+" to PATH, and other settings
# This is specific to your system
module load starCCM

starccm+ \
    -new -rsh /usr/bin/ssh \
    -licpath 1999@flex.cd-adapco.com -power -podkey $mypodkey -np $numberprocessors \
    -batch $macro -collab

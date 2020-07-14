This directory contains the scripts to perform the individual CFD simulations described in section 2.3 of the manuscript.


## How to run

In order to create a fresh setup run:
```
bash case-setup.sh
```

This will create a large number of case directories in `IDs'.
Each contains a `run.sh' file, which is a simple script to run each case locally.
Ideally, you create a run script which adapts to your computing resources and its workload manager, such as SLURM.


The actually used case setup for the study is contained in `ids.tar.gz'.
It may contain manual changes to the simulation setup script when the default setup did not converge.

## How to get the results

When the simulations have been run, you can collect the result data by running

```
bash collect-results.sh
```

Result data will be output in to the subdirectory `results'.
The created csv files have than to be plugged in to the `results.xslx' file.

The summary sheet represents the `Results.csv' file in the `figures' directory of this repository

 
## Mesh dependency study

The mesh-dependency directory contains a number of setups tested for mesh dependency.
They can be run the same way as described by the `run.sh' file mentioned above.

#!/bin/bash
# Version 17.07.2018
#################### Job Settings #################################################################
# Specific Commands for the work load manager SLURM are lines beginning with "#SBATCH"
#SBATCH -J star_dm            # Setting the display name for the submission
#SBATCH -N 2                  # Number of nodes to reserve, -N 2-5  for variable number of requested node count
#SBATCH --ntasks-per-node 16  # typically 16, range: 1..16 (max 16 cores per node)
#SBATCH -t 001:00:00          # set walltime in hours, format:    hhh:mm:ss, days-hh, days-hhh:mm:ss
#SBATCH -p short              # Desired Partition, alternatively comment this line out and submit the script with 'sbatch -p big jobscript.sh'


#################### Simulation Settings ##########################################################
## Work directory. No "/" at the end.
WORKDIR="/scratch/tmp/myUserName/dm"

## Name of the Design Manager Project. Must be in the WORKDIR.
PROJECTFILE="myProject.dmprj"

## Number of Processes per job, ideally 1,2,4,8,16,32,... processes per job (2^n for n=0,1,2,3,...)
NUMBER_OF_PROCCESSES_PER_JOB=16

## Settings for Design Manager (PROJECTSETTINGS) 
#PROJECTSETTINGS="-server -collab"   # Open Design Study in server mode (interactive)
PROJECTSETTINGS="-batch run -collab" # Run Design Study in batch mode

## Settings for individual simulations during Design Study
PASSTODESIGN="-collab"

## Personal POD key (22 characters)
PERSONAL_PODKEY="XXXXXXXXXXXX"

## Decide which StarCCM+ version to be used
module load starCCM/13.02.013


#################### Default Options. ##########################################################
##Number of Simultanious jobs depending on requesting nodes
NUMBER_OF_SIMULTANEAOUS_JOBS=$(( (${SLURM_JOB_NUM_NODES} * 16 ) / ${NUMBER_OF_PROCCESSES_PER_JOB} ))

MACHINEFILE="machinefile.$SLURM_JOBID.txt"

## License options
LICENSEOPTIONS="-podkey $PERSONAL_PODKEY -licpath 1999@flex.cd-adapco.com"


#################### Printing some Debug Information ##############################################
# simplify debugging:
echo "SLURM_JOB_NODELIST=$SLURM_JOB_NODELIST"
echo "SLURM_NNODES=$SLURM_NNODES SLURM_TASKS_PER_NODE=$SLURM_TASKS_PER_NODE"
env | grep -e MPI -e SLURM
echo "host=$(hostname) pwd=$(pwd) ulimit=$(ulimit -v) \$1=$1 \$2=$2"
exec 2>&1 # send errors into stdout stream

# list and echo loaded Modules
echo "Loaded Modules: $LOADEDMODULES"

export OMP_WAIT_POLICY="PASSIVE"
export OMP_NUM_THREADS=$((16/((SLURM_NPROCS+SLURM_NNODES-1)/SLURM_NNODES)))
[ $OMP_NUM_THREADS == 16 ] && export GOMP_CPU_AFFINITY="0-15:1" # task-specifique
export OMP_PROC_BIND=TRUE
echo OMP_NUM_THREADS=$OMP_NUM_THREADS

[ "$SLURM_NNODES" ] && [ $SLURM_NNODES -lt 4 ] && srun bash -c "echo task \$SLURM_PROCID of \$SLURM_NPROCS runs on \$SLURMD_NODENAME"



#################### Preparing Simulation #########################################################
## Change into Work Directory
cd $WORKDIR; echo pwd=$(pwd)

## Writing Machinefile
scontrol show hostnames $SLURM_JOB_NODELIST > $WORKDIR/$MACHINEFILE
sed -i 's/$/:16/' $WORKDIR/$MACHINEFILE

## Setting Design Manager's run settings
# Number of Simultanious Jobs
sed -r -i "s/'NumSimultaneousJobs': [0-9]+/'NumSimultaneousJobs': ${NUMBER_OF_SIMULTANEAOUS_JOBS}/" $WORKDIR/$PROJECTFILE
# Number of Processes per Computation
sed -r -i "s/'NumComputeProcesses': [0-9]+/'NumComputeProcesses': ${NUMBER_OF_PROCCESSES_PER_JOB}/" $WORKDIR/$PROJECTFILE

echo "Design Manager is running ${NUMBER_OF_SIMULTANEAOUS_JOBS} jobs of ${NUMBER_OF_PROCCESSES_PER_JOB} cores each."

#################### Running the simulation #######################################################
## Write start time stamp to the log file
echo "Starting the StarCCM+ Design Manager"
date +%Y-%m-%d_%H:%M:%S_%s_%Z # date as YYYY-MM-DD_HH:MM:SS_Ww_ZZZ

## Command to open StarCCM+ Design Manager
starlaunch jobmanager --rsh /usr/bin/ssh --resourcefile $WORKDIR/$MACHINEFILE --slots 0 \
     --command "starccm+ -preallocpower -rsh /usr/bin/ssh $WORKDIR/$PROJECTFILE $PROJECTSETTINGS ${LICENSEOPTIONS} \
         -passtodesign \" -rsh /usr/bin/ssh $PASSTODESIGN ${LICENSEOPTIONS}\" " \
     > $WORKDIR/$PROJECTFILE.$SLURM_JOBID.log 2>&1

## Write final time stamp to the log file
echo "Job finalised at:"
date +%Y-%m-%d_%H:%M:%S_%s_%Z
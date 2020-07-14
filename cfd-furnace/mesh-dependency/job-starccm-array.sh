#!/bin/bash
#################### Job Settings #################################################################
# Specific Commands for the work load manager SLURM are lines beginning with "#SBATCH"
#SBATCH -J tbr_meshDep  # Setting the display name for the submission
#SBATCH -N 3                  # Number of nodes to reserve, per job step
#SBATCH --ntasks-per-node 16  # typically 16, range: 1..16 (max 16 cores per node)
#SBATCH -t 10-010:00:00          # set walltime in hours, format:    hhh:mm:ss, days-hh, days-hhh:mm:ss
#SBATCH -p long              # Desired Partition, alternatively comment this line out and submit the script with 'sbatch -p big jobscript.sh
#SBATCH --mem 120G            # Required Memory
#SBATCH --exclusive=user      # Don't share nodes
#SBATCH -o logs/slurm-%A_%a.out    # individual SLURM log files

#SBATCH --array=1-10%3        # Running an Job Array      

#SBATCH --nice 100            # Priority Adjustment

#################### Mechthild Specific Setup #####################################################
# Setup OpenMP
if [ -n "$SLURM_CPUS_PER_TASK" ]; then  omp_threads=$SLURM_CPUS_PER_TASK; else   omp_threads=1; fi
export OMP_NUM_THREADS=$omp_threads
# load the modules system
source /etc/profile.d/modules.sh


#################### Simulation Settings ##########################################################
## Work directory. No "/" at the end.
ROOTDIR=""

WORKDIR=$ROOTDIR/$(sed -n "${SLURM_ARRAY_TASK_ID}p" $ROOTDIR/simdirs.array)
 
## Simulation File (location in work directory)
SIMULATIONFILE="-new"
#SIMULATIONFILE="$WORKDIR/star.sim"
 
## Macro file (location in work directory)
MACROFILE="reactor.java"
 
## Personal POD key
PERSONAL_PODKEY=""
 
## Decide which version by commenting out the desired version. 
module load apps/starCCM/13.02.013
 
## Application. Can be kept constant if modules are used.
APPLICATION="starccm+"
OPTIONS="$SIMULATIONFILE -batch $WORKDIR/$MACROFILE -licpath 1999@flex.cd-adapco.com -power -podkey $PERSONAL_PODKEY -collab -time -rsh /usr/bin/ssh -mpi intel"

#################### Printing some Debug Information ##############################################
# simplify debugging:
echo "SLURM_JOB_NODELIST=$SLURM_JOB_NODELIST"
echo "SLURM_NNODES=$SLURM_NNODES SLURM_TASKS_PER_NODE=$SLURM_TASKS_PER_NODE"
env | grep -e MPI -e SLURM
echo "host=$(hostname) pwd=$(pwd) ulimit=$(ulimit -v) \$1=$1 \$2=$2"
exec 2>&1 # send errors into stdout stream

## Loading required modules
module load mpi/intel/2018.1 

# list and echo loaded Modules
echo "Loaded Modules: $LOADEDMODULES"
 
## Change into Work Directory
cd $WORKDIR; echo pwd=$(pwd) 
# 
echo OMP_NUM_THREADS=$OMP_NUM_THREADS
 
[ "$SLURM_NNODES" ] && [ $SLURM_NNODES -lt 4 ] && srun bash -c "echo task \$SLURM_PROCID of \$SLURM_NPROCS runs on \$SLURMD_NODENAME"


#################### Preparing the Simulation #####################################################
## creating machinefile & temp in work directory
MACHINEFILE="machinefile.$SLURM_JOBID.txt"
scontrol show hostnames $SLURM_JOB_NODELIST > $WORKDIR/$MACHINEFILE


#################### Running the simulation #######################################################
## Let StarCCM+ wait for licenses on startup
export STARWAIT=1
 
## Start time stamp
echo "Start of the simulation: $(date +%Y-%m-%d_%H:%M:%S_%s_%Z)" # date as YYYY-MM-DD_HH:MM:SS_Ww_ZZZ
 
## Command to run application (StarCCM+)
#$APPLICATION $OPTIONS -np $SLURM_NPROCS -machinefile $WORKDIR/$MACHINEFILE > $WORKDIR/$SIMULATIONFILE.$SLURM_JOBID.output.log 2>&1
$APPLICATION $OPTIONS -np $SLURM_NPROCS -machinefile $WORKDIR/$MACHINEFILE > $WORKDIR/reactor.$SLURM_JOBID.output.log 2>&1
 
 ## Final time stamp
echo "Simulation finalized at: $(date +%Y-%m-%d_%H:%M:%S_%s_%Z)"

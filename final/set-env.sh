#! /bin/bash

export LC_ALL=C

command -v scalas || {
  echo 'sbt script runner not set up.'
  echo 'Details: http://www.scala-sbt.org/release/docs/Detailed-Topics/Scripts'
  exit 1
}

# Modify to match your intall paths
# Then use 'source ./set-env.sh' to set environment

# Install directory of IRSTLM
# Details: http://sourceforge.net/projects/irstlm/
export IRSTLM=$HOME/tools/irstlm
echo $IRSTLM

# Install directory of Moses decoder
# Details: http://www.statmt.org/moses
export MOSES=$HOME/tools/mosesdecoder
echo $MOSES

# The single executable of hunalign
# Details: http://mokk.bme.hu/resources/hunalign/
export HUNALIGN_BIN=$HOME/tools/hunalign-1.1/src/hunalign/hunalign
echo $HUNALIGN_BIN

# Install directory of GIZA++
# Details: http://code.google.com/p/giza-pp/
export GIZAPP=$HOME/tools/giza-pp
echo $GIZAPP

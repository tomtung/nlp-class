#! /bin/bash

source ../set-env.sh

rm -r giza.*
rm -r corpus
rm -r model

mkdir external-bin
cp $GIZAPP/GIZA++-v2/GIZA++ ./external-bin/GIZA++
cp $GIZAPP/GIZA++-v2/snt2cooc.out ./external-bin/snt2cooc.out
cp $GIZAPP/mkcls-v2/mkcls ./external-bin/mkcls

$MOSES/scripts/training/train-model.perl --first-step 1 --last-step 9 \
  --corpus ../word-align/shiji.clean -f modern -e classical \
  --lm 0:3:`realpath ../language-model/lm.b.classical`:8 \
  -external-bin-dir ./external-bin -root-dir .

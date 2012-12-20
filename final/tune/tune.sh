#! /bin/bash

. ../set-env.sh

rm -r mert-work

$MOSES/scripts/training/mert-moses.pl ./shiji.dev.tokenized.modern ./shiji.dev.tokenized.classical \
  $MOSES/bin/moses ../train/model/moses.ini --mertdir $MOSES/bin --decoder-flags="-threads 4"

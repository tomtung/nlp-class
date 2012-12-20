#! /bin/bash

source ../set-env.sh

rm -r giza.*
rm -r corpus
rm -r model

mkdir giza.classical-modern
mkdir giza.modern-classical

cp ../word-align/shiji.wa.classical-modern ./giza.classical-modern/classical-modern.A3.final
gzip -n ./giza.classical-modern/classical-modern.A3.final

cp ../word-align/shiji.wa.modern-classical ./giza.modern-classical/modern-classical.A3.final
gzip -n ./giza.modern-classical/modern-classical.A3.final

mkdir external-bin
cp $GIZAPP/GIZA++-v2/GIZA++ ./external-bin/GIZA++
cp $GIZAPP/GIZA++-v2/snt2cooc.out ./external-bin/snt2cooc.out
cp $GIZAPP/mkcls-v2/mkcls ./external-bin/mkcls

$MOSES/scripts/training/train-model.perl --first-step 3 --last-step 9 \
  --corpus ../word-align/shiji.clean -f modern -e classical \
  --lm 0:3:`realpath ../language-model/lm.b.classical`:8 \
  -external-bin-dir ./external-bin -root-dir .

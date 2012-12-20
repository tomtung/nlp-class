#! /bin/bash

. ../set-env.sh

../tokenization/tokenize.scala naive ../corpus/shiji/shiji.dev.classical shiji.dev.tokenized.naive.classical
../sentence-alignment/sent-segment.scala shiji.dev.tokenized.naive.classical shiji.dev.sent.classical

../tokenization/tokenize.scala naive ../corpus/shiji/shiji.dev.modern shiji.dev.tokenized.naive.modern
../sentence-alignment/sent-segment.scala shiji.dev.tokenized.naive.modern shiji.dev.sent.modern

touch null.dict
$HUNALIGN_BIN -text -realign -utf -cautious -thresh=0 -bisent \
  null.dict ./shiji.dev.sent.classical ./shiji.dev.sent.modern > shiji.dev.sent.aligned
rm null.dict

../sentence-alignment/hunaligned-to-bitext.scala shiji.dev.sent.aligned shiji.dev.sent.aligned.classical shiji.dev.sent.aligned.modern
sed -i "s/\s//g" shiji.dev.sent.aligned.classical
sed -i "s/\s//g" shiji.dev.sent.aligned.modern

../tokenization/tokenize.scala --vocab ../learn-vocabulary/shiji-vocab mfm shiji.dev.sent.aligned.classical shiji.dev.tokenized.classical
../tokenization/tokenize.scala --vocab ../learn-vocabulary/shiji-vocab crf shiji.dev.sent.aligned.modern shiji.dev.tokenized.modern

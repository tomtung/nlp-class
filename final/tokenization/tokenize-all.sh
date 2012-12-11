#! /bin/bash

echo "Tokenizing classical..."
./tokenize.scala --vocab ../learn-vocabulary/shiji-vocab mfm ../sentence-alignment/shiji.subsent.aligned.classical shiji.tokenized.classical

echo "Tokenizing modern..."
./tokenize.scala --vocab ../learn-vocabulary/shiji-vocab crf ../sentence-alignment/shiji.subsent.aligned.modern shiji.tokenized.modern

#! /bin/bash

echo "Tokenizing classical..."
./tokenize.scala --vocab ../learn-vocabulary/shiji-vocab mfm ../corpus/shiji/shiji.classical shiji.tokenized.classical

echo "Tokenizing modern..."
./tokenize.scala --vocab ../learn-vocabulary/shiji-vocab crf ../corpus/shiji/shiji.modern shiji.tokenized.modern

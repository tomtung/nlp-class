#! /bin/bash

echo "Tokenizing classical..."
./tokenize.scala --vocab ../learn-vocabulary/shiji-vocab mfm ../sentence-alignment/shiji.subsent.aligned.classical shiji.tokenized.classical

echo "Tokenizing modern..."
./tokenize.scala --vocab ../learn-vocabulary/shiji-vocab crf ../sentence-alignment/shiji.subsent.aligned.modern shiji.tokenized.modern

echo "Tokenizing classical(train)..."
./tokenize.scala --vocab ../learn-vocabulary/shiji-vocab mfm ../sentence-alignment/shiji.train.subsent.aligned.classical shiji.train.tokenized.classical

echo "Tokenizing modern(train)..."
./tokenize.scala --vocab ../learn-vocabulary/shiji-vocab crf ../sentence-alignment/shiji.train.subsent.aligned.modern shiji.train.tokenized.modern

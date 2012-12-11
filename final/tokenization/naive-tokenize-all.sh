#! /bin/bash

echo "Tokenizing classical..."
./tokenize.scala naive ../corpus/shiji/shiji.classical shiji.tokenized.naive.classical

echo "Tokenizing modern..."
./tokenize.scala naive ../corpus/shiji/shiji.modern shiji.tokenized.naive.modern

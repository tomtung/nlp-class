#! /bin/bash

echo "Tokenizing classical..."
./tokenize.scala classical ../corpus/shiji/shiji.classical shiji.tokenized.classical

echo "Tokenizing modern..."
./tokenize.scala modern ../corpus/shiji/shiji.modern shiji.tokenized.modern

#! /bin/bash

echo "Tokenizing classical..."
./tokenize.scala naive ../corpus/shiji/shiji.classical shiji.tokenized.naive.classical

echo "Tokenizing modern..."
./tokenize.scala naive ../corpus/shiji/shiji.modern shiji.tokenized.naive.modern

echo "Tokenizing classical(train)..."
./tokenize.scala naive ../corpus/shiji/shiji.train.classical shiji.train.tokenized.naive.classical

echo "Tokenizing modern(train)..."
./tokenize.scala naive ../corpus/shiji/shiji.train.modern shiji.train.tokenized.naive.modern

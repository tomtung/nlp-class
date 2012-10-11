#!/bin/sh

cat ../../3/strings.novowels | carmel -sribWEIk 1 ../word2letters-invert.fst unigram.fsa ../letters2word-invert.fst ../../3/remove-vowels.fst > strings.restored

echo
echo "Accuracy:"
python ../../../eval.py ../../../strings strings.restored
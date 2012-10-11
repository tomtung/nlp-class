#!/bin/sh

cat ../3/strings.novowels | carmel -sribWEIk 1 ../2/english.fsa ../3/remove-vowels.fst > strings.restored

echo
echo "Accuracy:"
python ../../eval.py ../../strings strings.restored
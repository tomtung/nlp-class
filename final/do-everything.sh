#! /bin/bash

# This script does everything, from initial data cleansing, to training, to tuning, to evaluating
# It shows the entire workflow
# set-env.sh needs to be edited first

echo 'Creating data files...'
cd corpus/shiji/
./cat-all.sh
cd -

echo 'Learning vocabulary...'
cd learn-vocabulary
./bitext-learn-vocab.sh
cd -

echo 'Sentence alignment - data preparation...'
cd tokenization
./naive-tokenize-all.sh
cd -

echo 'Sentence alignment - first pass...'
cd sentence-alignment
./sent-align.sh
echo 'Sentence alignment - second pass...'
./subsent-align.sh
cd -

echo 'Tokenization...'
cd tokenization
./tokenize-all.sh
cd -

echo 'Train language model...'
cd language-model
./train-lm.sh
cd -

echo 'Word alignment - data preparation...'
cd word-align
./clean.sh
echo 'Word alignment...'
./align.sh
cd -

echo 'Train phrase-based...'
cd train
./train.sh
cd -

echo 'Tuning the model...'
cd tune
./tune-prep.sh
./tune.sh
cd -

echo 'Evaluate model...'
cd evaluation
./eval-model.sh > eval-model.result
echo 'Evaluate baseline...'
./eval-baseline.sh > eval-baseline.result
cd -

echo 'Done' 

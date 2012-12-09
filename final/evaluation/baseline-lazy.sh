#! /bin/bash

echo 'Evaluating lazy do-nothing baseline...'
./bleu-eval.scala ../corpus/shiji/shiji.classical ../corpus/shiji/shiji.modern

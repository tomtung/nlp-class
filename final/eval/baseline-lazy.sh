#! /bin/bash

echo 'Evaluating lazy do-nothing baseline...'
./bleu-eval.scala ../corpus/shiji/classical-zh_cn ../corpus/shiji/modern

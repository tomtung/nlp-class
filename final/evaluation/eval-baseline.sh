#! /bin/bash

cp ../corpus/shiji/shiji.dev.modern baseline-output.dev
sed -i -e "s/的/之/g" -e "s/说/曰/g" -e "s/到/至/g" \
  -e "s/秦国/秦/g" -e "s/楚国/楚/g" -e "s/齐国/齐/g" \
  -e "s/了//g" -e "s/他们//g" -e "s/所以//g" -e "s/军队//g" \
  -e "s/在//g" -e "s/他//g" -e "s/就//g" -e "s/是//g" -e "s/这//g" \
  baseline-output.dev

echo 'Evaluating baseline on dev...'
./bleu-eval.scala ../corpus/shiji/shiji.dev.classical baseline-output.dev

cp ../corpus/shiji/shiji.test.modern baseline-output.test
sed -i -e "s/的/之/g" -e "s/说/曰/g" -e "s/到/至/g" \
  -e "s/秦国/秦/g" -e "s/楚国/楚/g" -e "s/齐国/齐/g" \
  -e "s/了//g" -e "s/他们//g" -e "s/所以//g" -e "s/军队//g" \
  -e "s/在//g" -e "s/他//g" -e "s/就//g" -e "s/是//g" -e "s/这//g" \
  baseline-output.test

echo 'Evaluating baseline on test...'
./bleu-eval.scala ../corpus/shiji/shiji.test.classical baseline-output.test

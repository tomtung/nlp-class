#! /bin/bash

cat `ls modern/* | sort` > shiji.modern
cat `ls classical-zh_cn/* | sort` > shiji.classical

total_nline=`wc -l < shiji.modern`
if [ "$total_nline" != `wc -l < shiji.classical` ]; then
  echo 'Error: different number of lines' >&2
  exit 1
fi

echo "Total line number: $total_nline"

declare -i train_nline=`echo "$total_nline * 80 / 100" | bc`
declare -i dev_nline=`echo "$total_nline * 10 / 100" | bc`
declare -i test_nline=`echo "$total_nline * 10 / 100" | bc`

echo "Train line number: $train_nline"
head -$train_nline shiji.modern > shiji.train.modern
head -$train_nline shiji.classical > shiji.train.classical

echo "Dev line number: $dev_nline"
tail +$(($train_nline + 1)) shiji.modern | head -$dev_nline > shiji.dev.modern
tail +$(($train_nline + 1)) shiji.classical | head -$dev_nline > shiji.dev.classical

echo "Test line number: $test_nline"
tail -$test_nline shiji.modern > shiji.test.modern
tail -$test_nline shiji.classical > shiji.test.classical

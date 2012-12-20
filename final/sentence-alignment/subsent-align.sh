#! /bin/bash

echo 'Note: the script should be run after sent-align.sh'

source ../set-env.sh

do_align () {
  echo 'Splitting sentences into sub-sentences...'
  ./sent-segment.scala --subsent $1.sent.aligned.classical $1.subsent.classical || { exit 1; }
  ./sent-segment.scala --subsent $1.sent.aligned.modern $1.subsent.modern || { exit 1; }

  echo 'Performing sub-sentence alignment...'

  ./hunalign-partial.scala --max-line-num 46000 hunalign-batch-jobs \
    $1.subsent.classical $1.subsent.modern $1.subsent classical modern

  touch null.dict
  $HUNALIGN_BIN -text -realign -utf -cautious -thresh=0 -batch null.dict hunalign-batch-jobs
  rm null.dict

  cat $1.subsent_*.aligned > $1.subsent.aligned

  echo 'Formatting result as bitext...'
  ./hunaligned-to-bitext.scala $1.subsent.aligned $1.subsent.aligned.classical $1.subsent.aligned.modern

  sed -i "s/\s//g" $1.subsent.aligned.classical
  sed -i "s/\s//g" $1.subsent.aligned.modern
}

echo "For full text"
do_align 'shiji'

echo "For training set only:"
do_align 'shiji.train'

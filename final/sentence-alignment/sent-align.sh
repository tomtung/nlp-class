#! /bin/bash

source ../set-env.sh

do_align () {
  echo 'Splitting paragraphs into sentences...'
  ./sent-segment.scala ../tokenization/$1.tokenized.naive.classical $1.sent.classical
  ./sent-segment.scala ../tokenization/$1.tokenized.naive.modern $1.sent.modern

  echo 'Performing sentence alignment...'
  touch null.dict
  $HUNALIGN_BIN -text -realign -utf -cautious -thresh=0 \
      null.dict ./$1.sent.classical ./$1.sent.modern > $1.sent.aligned || {
	  echo "Error: Something was wrong... maybe there isn't enough memory." >&2;
	  echo 'This script works fine on 64bit linux with 5+G RAM.' >&2;
	  echo "You can use hunalign-partial.scala to split the $1.sent.* files into smaller files and use hunalign in batch mode." >&2;
	  exit 1;
      }
  rm null.dict
  
  echo 'Formatting result as bitext...'
  ./hunaligned-to-bitext.scala $1.sent.aligned $1.sent.aligned.classical $1.sent.aligned.modern
}

echo "For full text:"
do_align 'shiji'

echo "For training set only:"
do_align 'shiji.train'

#! /bin/bash

# Note: the script should be run after sent-align.sh

command -v hunalign > /dev/null || {
    echo 'Error: hunalign not found!' >&2;
    echo 'It is available at: http://mokk.bme.hu/resources/hunalign/' >&2;
    exit 1;
}

echo 'Splitting sentences into sub-sentences...'
./sent-segment.scala --subsent shiji.sent.aligned.classical shiji.subsent.classical || { exit 1; }
./sent-segment.scala --subsent shiji.sent.aligned.modern shiji.subsent.modern || { exit 1; }

echo 'Performing sub-sentence alignment...'

./hunalign-partial.scala --max-line-num 46000 hunalign-batch-jobs \
  shiji.subsent.classical shiji.subsent.modern shiji.subsent classical modern

touch dict
hunalign -text -realign -utf -cautious -thresh=0 -batch dict hunalign-batch-jobs

cat shiji.subsent_*.aligned > shiji.subsent.aligned

echo 'Formatting result as bitext...'
./hunaligned-to-bitext.scala shiji.subsent.aligned shiji.subsent.aligned.classical shiji.subsent.aligned.modern

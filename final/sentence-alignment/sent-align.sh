#! /bin/bash

command -v hunalign > /dev/null || {
    echo 'Error: hunalign not found!' >&2;
    echo 'It is available at: http://mokk.bme.hu/resources/hunalign/' >&2;
    exit 1;
}

echo 'Splitting paragraphs into sentences...'
./sent-segment.scala ../tokenization/shiji.tokenized.naive.classical shiji.sent.classical
./sent-segment.scala ../tokenization/shiji.tokenized.naive.modern shiji.sent.modern

echo 'Performing sentence alignment...'
touch null.dict
hunalign -text -realign -utf -cautious -thresh=0 \
    null.dict ./shiji.sent.classical ./shiji.sent.modern > shiji.sent.aligned || {
        echo "Error: Something was wrong... maybe there isn't enough memory." >&2;
        echo 'This script works fine on 64bit linux with 5+G RAM.' >&2;
        echo 'You can use hunalign-partial.scala to split the shiji.sent.* files into smaller files and use hunalign in batch mode.' >&2;
        exit 1;
    }
rm null.dict

echo 'Formatting result as bitext...'
./hunaligned-to-bitext.scala shiji.sent.aligned shiji.sent.aligned.classical shiji.sent.aligned.modern

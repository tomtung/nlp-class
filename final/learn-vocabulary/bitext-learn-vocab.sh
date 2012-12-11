#! /bin/bash

TO_EXCLUDE_REGEX='.*[一二两三四五六七八九十百千万之并入以攻败等击杀好人大小谁能为死而我有伐时在叛破救动习附面所法年月用者母受嫁].*|妻子|户封|君子听|其.|知其|.+于|不.*|.*不|归汉|.将|将.|父子相|与.*|.上|.下|.中|无.*|出.|各得其|水灌'

echo 'Learning candidate vocabulary from modern text...'
./learn-vocabulary.scala -l 3 -c 5 -i 3.5 -e 1 -o shiji-vocab.modern -s --exclude $TO_EXCLUDE_REGEX ../corpus/shiji/shiji.modern

echo 'Learning candidate vocabulary from classical text...'
./learn-vocabulary.scala -l 3 -c 3 -i 3.5 -e 0.8 -o shiji-vocab.classical -s --exclude $TO_EXCLUDE_REGEX ../corpus/shiji/shiji.classical

echo 'Learning vocabulary from bi-text...'
./learn-bitext-vocabulary.scala --out shiji-vocab.tmp --vocab-in shiji-vocab.modern,shiji-vocab.classical ../corpus/shiji/shiji.classical ../corpus/shiji/shiji.modern

cat shiji-vocab-static >> shiji-vocab.tmp

sort shiji-vocab.tmp | uniq > shiji-vocab

rm shiji-vocab.tmp

# sed -e 's/^/\^/' -e 's/$/\\\\s/' shiji-vocab | xargs -I{} grep --color=always {} shiji-vocab.classical shiji-vocab.modern | less

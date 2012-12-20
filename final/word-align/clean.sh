#! /bin/bash

./clean.scala --exclude-regex '.*。(?!\s”$|$).*' ../tokenization/shiji.train.tokenized modern classical shiji.clean

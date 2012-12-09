#! /bin/bash

cat `ls modern/* | sort` > shiji.modern
cat `ls classical-zh_cn/* | sort` > shiji.classical

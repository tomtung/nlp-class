#!/usr/bin/env python

from treeBank import TreeBank

train_words = TreeBank.from_file('./data/train.trees').collect_vocabulary()

dev_words = TreeBank.from_file('./data/dev.trees').collect_vocabulary()

diff = dev_words.difference(train_words)
print(diff)
print(len(diff))

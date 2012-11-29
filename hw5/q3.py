#!/usr/bin/env python

from treeBank import TreeBank

tb = TreeBank.from_file("./data/train.trees")
tb.normalize()

print(tb.collect_rule_set().size)

#!/usr/bin/env python

from treeBank import TreeBank

ans = max([len(node.children)
           for t in TreeBank.from_file('./data/train.trees').trees
           for node in t.bottom_up()])

print(ans)

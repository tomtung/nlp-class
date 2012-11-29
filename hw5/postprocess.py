#!/usr/bin/env python

import fileinput
import tree

for line in fileinput.input():
    t = tree.Tree.from_str(line)

    t.restore_unit()
    t.unbinarize()

    print(t)
    
    

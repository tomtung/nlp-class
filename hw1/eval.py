#!/usr/bin/env python

import sys, itertools, re

try:
    goldfilename, testfilename = sys.argv[1:]
except:
    sys.stderr.write("usage: eval.py <gold> <test>\n")
    sys.exit(1)

def uncarmelize(s):
    toks = s.split()
    result = []
    for tok in toks:
        m = re.match(r'^"(.)"$', tok)
        if not m:
            sys.stderr.write("error reading file\n")
            sys.exit(1)
        result.append(m.group(1))
    return "".join(result)

m = n = 0
for goldline, testline in itertools.izip(open(goldfilename), open(testfilename)):
    goldwords = uncarmelize(goldline).split("_")
    testwords = uncarmelize(testline).split("_")
    for goldword, testword in itertools.izip(goldwords, testwords):
        n += 1
        if goldword == testword:
            m += 1

print float(m)/n

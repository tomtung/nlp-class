#!/usr/bin/env python

import collections
import sys
import tree

def _brackets_helper(node, i, result):
    i0 = i
    if len(node.children) > 0:
        for child in node.children:
            i = _brackets_helper(child, i, result)
        j0 = i
        if len(node.children[0].children) > 0: # don't count preterminals
            result[node.label, i0, j0] += 1
    else:
        j0 = i0 + 1
    return j0


def brackets(t):
    result = collections.defaultdict(int)
    _brackets_helper(t.root, 0, result)
    return result


def main(parse_filename, gold_filename):
    match_count = parse_count = gold_count = 0
    for parse_line, gold_line in zip(open(parse_filename), open(gold_filename)):
        gold = tree.Tree.from_str(gold_line)
        gold_brackets = brackets(gold)
        gold_count += sum(gold_brackets.values())

        if parse_line.strip() in ["0", ""]:
            continue

        parse = tree.Tree.from_str(parse_line)
        parse_brackets = brackets(parse)
        parse_count += sum(parse_brackets.values())

        for bracket, count in parse_brackets.items():
            match_count += min(count, gold_brackets[bracket])
    print("%s\t%d brackets" % (parse_filename, parse_count))
    print("%s\t%d brackets" % (gold_filename, gold_count))
    print("matching\t%d brackets" % match_count)
    print("precision\t%s" % (float(match_count) / parse_count))
    print("recall\t%s" % (float(match_count) / gold_count))
    print("F1\t%s" % (2. / (gold_count / float(match_count) + parse_count / float(match_count))))

if __name__ == "__main__":
    try:
        _, parse_filename, gold_filename = sys.argv
        main(parse_filename, gold_filename)
    except:
        sys.stderr.write("usage: evalb.py <parse-file> <gold-file>\n")
        sys.exit(1)

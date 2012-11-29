#!/usr/bin/env python

from treeBank import TreeBank
import pcfg

train_golden_filename = "./data/train.trees"

dev_input_filename = './data/dev.strings'
dev_output_filename = './output/dev.output'
dev_golden_filename = './data/dev.trees'

test_input_filename = './data/test.strings'
test_output_filename = './output/test.output'
test_golden_filename = './data/test.trees'

def train_on(filename):
    tb = TreeBank.from_file(filename)
    tb.normalize()
    rule_set = tb.collect_rule_set(vertical_markov=True, vertical_markov_factor=0.8)
    return rule_set


def parse_and_eval(rule_set, input_filename, output_filename, golden_filename):
    with open(input_filename) as in_f, open(output_filename, 'w') as out_f:
        for l in in_f:
            tokens = l.split()
            #import time
            #t_start = time.time()
            t = pcfg.cky_parse(tokens, rule_set)
            #t_end = time.time()
            #t_diff = t_end - t_start
            #print("{%s, %s}," % (len(tokens), t_diff))
            if not t:
                out_f.write("0\n")
            else:
                t.unnormalize()
                out_f.write("%s\n" % t)
    import evalb

    evalb.main(output_filename, golden_filename)


rs = train_on(train_golden_filename)

print('Performance on dev:')
parse_and_eval(train_on(train_golden_filename), dev_input_filename, dev_output_filename, dev_golden_filename)
print()

#print('Performance on dev (trained on dev golden):')
#parse_and_eval(train_on(dev_golden_filename), dev_input_filename, dev_output_filename, dev_golden_filename)
#print()

print('Performance on test:')
parse_and_eval(train_on(train_golden_filename), test_input_filename, test_output_filename, test_golden_filename)
print()

#print('Performance on test (trained on test golden):')
#parse_and_eval(train_on(test_golden_filename), test_input_filename, test_output_filename, test_golden_filename)
#print()
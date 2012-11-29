import collections
import tree
import pcfg

__author__ = 'Tom Dong'

class TreeBank(object):
    def __init__(self, trees=None):
        if not trees: trees = []
        self.trees = trees
        self.start_sym = "TOP"

    def count_word_freq(self, case_sensitive=False) -> dict:
        count = collections.defaultdict(int)
        for tree in self.trees:
            for leaf in tree.leaves():
                if case_sensitive:
                    count[leaf.label] += 1
                else:
                    count[leaf.label.lower()] += 1
        return count

    def collect_vocabulary(self, case_sensitive=False):
        return set(self.count_word_freq(case_sensitive).keys())

    def collect_rule_set(self, case_sensitive=False,
                         confuse_rare_words=True,
                         rare_word_threshold=1,
                         replace_unknown_word=pcfg.replace_unknown_word_default,
                         vertical_markov=False,
                         vertical_markov_factor=0.6):
        word_freq = self.count_word_freq(case_sensitive)
        rule_prob = collections.defaultdict(float)
        left_sum = collections.defaultdict(float)

        for tree in self.trees:
            for node in tree.bottom_up():
                if node.children: # Left should be non-terminal
                    if not vertical_markov:
                        left = node.label
                    else:
                        p = node.parent.label if node.parent else None
                        left = (node.label, p)

                    if not node.children[0].children: # Node is pre-terminal
                        assert len(node.children) == 1
                        w = node.children[0].label
                        if not case_sensitive:
                            w = w.lower()

                        if confuse_rare_words and word_freq[w] <= rare_word_threshold:
                            right = (replace_unknown_word(node.children[0].label),)
                        else:
                            right = (w,)
                    else:
                        right = tuple(map(lambda c: c.label, node.children))

                    if not vertical_markov:
                        rule = pcfg.Rule(left, right)
                        rule_prob[rule] += 1
                        left_sum[left] += 1
                    else:
                        if vertical_markov_factor > 0:
                            rule = pcfg.Rule(left, right)
                            rule_prob[rule] += vertical_markov_factor
                        if 1 - vertical_markov_factor > 0:
                            rule_bkoff = pcfg.Rule(left[0], right)
                            rule_prob[rule_bkoff] += 1 - vertical_markov_factor
                        left_sum[left[0]] += 1

        for rule in rule_prob.keys():
            if type(rule.left) == tuple:
                rule_prob[rule] /= left_sum[rule.left[0]]
            else:
                rule_prob[rule] /= left_sum[rule.left]

        return pcfg.RuleSet(rule_prob, self.start_sym)

    def normalize(self):
        for tree in self.trees:
            tree.normalize()

    def unnormalize(self):
        for tree in self.trees:
            tree.unnormalize()

    @staticmethod
    def from_file(path : str):
        tb = TreeBank()
        f = open(path)
        for str in f.readlines():
            tb.trees.append(tree.Tree.from_str(str))
        f.close()
        return tb

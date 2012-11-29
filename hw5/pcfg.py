import collections
from bigFloat import BigFloat
import tree

__author__ = 'Tom Dong'

Rule = collections.namedtuple("Rule", ["left", "right"])
Rule.__str__ = lambda self: '%s -> %s' % (self.left, ' '.join(self.right))

class RuleSet(object):
    def __init__(self, rule_prob=None, start_sym="TOP"):
        self.start_sym = start_sym
        if not rule_prob: rule_prob = {}
        self._rule_prob = rule_prob.copy()
        self._left_to_right = collections.defaultdict(set)
        self._right_to_left = collections.defaultdict(set)
        for rule in rule_prob.keys():
            self._left_to_right[rule.left].add(rule.right)
            self._right_to_left[rule.right].add(rule.left)

    @property
    def rules(self):
        for rule, prob in self._rule_prob.items():
            yield rule, prob

    @property
    def size(self):
        return len(self._rule_prob)

    def rules_with_left(self, left):
        for right in self._left_to_right[left]:
            rule = Rule(left, right)
            yield rule, self._rule_prob[rule]

    def has_left(self, left):
        if  self._left_to_right[left]:
            return True
        else:
            return False

    def rules_with_right(self, right):
        if type(right) != tuple: #terminal
            right = (right,)

        if all(map(lambda s: type(s) == str, right)):
            for left in self._right_to_left[right]:
                rule = Rule(left, right)
                yield rule, self._rule_prob[rule]
        elif all(map(lambda t: type(t) == tuple, right)) and len(set(map(lambda t: t[1], right))) == 1:
            left_label = right[0][1]
            right_labels = tuple(map(lambda t: t[0], right))
            for left in self._right_to_left[right_labels]:
                if left == left_label or type(left) == tuple and left[0] == left_label:
                    rule = Rule(left, right_labels)
                    yield rule, self._rule_prob[rule]

    def has_right(self, right):
        if type(right) != tuple:
            right = (right,)
        if  self._right_to_left[right]:
            return True
        else:
            return False

    def rule_prob(self, rule):
        self._rule_prob.get(rule, 0)


def replace_unknown_word_default(w: str) -> str:
#    if w.endswith("ing"):
#        return "<unk-ing>"
#    if w.endswith("s"):
#        return "<unk-s>"
#    if w.endswith("ed"):
#        return "<unk-ed>"
#    if w[0].isupper():
#        return "<unk-Upper>"
#    if len(w) == 1:
#        return "<unk-letter>"
#    if w.isnumeric():
#        return "<unk-num>"
#    if not w[0].isalpha():
#        return "<unk-'>"
    return "<unk>"


def cky_parse(tokens: list,
              rule_set: RuleSet,
              case_sensitive: bool=False,
              replace_unknown_word=replace_unknown_word_default):
    # make sure the rules are binarized
    for rule, prob in rule_set.rules:
        assert len(rule.right) == 1 or len(rule.right) == 2
        if len(rule.right) == 1:
            assert not rule_set.has_left(rule.right[0])

    length = len(tokens)

    def preprocess_word(w):
        ans = w
        if not case_sensitive:
            ans = w.lower()
        if not rule_set.has_right(ans):
            ans = replace_unknown_word(w)
        return ans

    tokens = list(map(preprocess_word, tokens))

    derivation = collections.namedtuple("derivation", ["k", "rule", "prob"])

    # best[i][j][X]: the best derivation for sub-sequence token[i:j] given the left side of rule is 'X'
    gen_row = lambda r: list(map(lambda l: dict() if l > r else None, range(length + 1)))
    best = list(map(gen_row, range(length)))

    def enum_derivations(i, j):
        assert i < j <= length
        if j == i + 1:
            right = tokens[i]
            for rule, prob in rule_set.rules_with_right(right):
                yield derivation(None, rule, BigFloat(prob))
        else:
            for k in range(i + 1, j):
                # enumerate all possible X->YZ
                for (_, (Y, _), probY) in best[i][k].values():
                    for (_, (Z, _), probZ) in best[k][j].values():
                        right = (Y, Z)
                        for rule, ruleProb in rule_set.rules_with_right(right):
                            prob = ruleProb * probY * probZ
                            yield derivation(k, rule, prob)

    def update_best(i, j, d : derivation):
        curr_best = best[i][j].get(d.rule.left)
        if not curr_best or d.prob > curr_best.prob:
            best[i][j][d.rule.left] = d

    for l in range(1, length + 1):
        for i in range(0, length - l + 1):
            j = i + l
            for d in enum_derivations(i, j):
                update_best(i, j, d)


    def build_tree(left, i, j):
        if type(left) == tuple:
            left_label = left[0]
        else:
            left_label = left

        if type(left) == str or not best[i][j].get(left):
            (k, rule, _) = best[i][j][left_label]
        else:
            (k, rule, _) = best[i][j][left]

        if j == i + 1:
            assert not k and len(rule.right) == 1 and rule.right[0] == tokens[i]
            child = tree.Node(tokens[i], [])
            return tree.Node(left_label, [child])
        else:
            assert len(rule.right) == 2
            child0 = build_tree((rule.right[0], left_label), i, k)
            child1 = build_tree((rule.right[1], left_label), k, j)
            return tree.Node(left_label, [child0, child1])

    def build_forest(i, j):
        if best[i][j].values():
            d = max(best[i][j].values(), key=lambda d: d.prob)
            return [build_tree(d.rule.left, i, j)], d.prob

        trees = []
        prob = BigFloat(-1)
        for k in range(i + 1, j):
            if best[i][k].values():
                d = max(best[i][k].values(), key=lambda dd: dd.prob)
                rest_trees, rest_prob = build_forest(k, j)
                if prob < d.prob * rest_prob:
                    trees = [build_tree(d.rule.left, i, k)] + rest_trees
                    prob = d.prob * rest_prob
        return trees, prob

    if not best[0][length].get(rule_set.start_sym) and not best[0][length].get((rule_set.start_sym, None)):
        children, _ = build_forest(0, length)
        root = tree.Node(rule_set.start_sym, children)
    else:
        root = build_tree((rule_set.start_sym, None), 0, length)
    return tree.Tree(root)

from treeBank import TreeBank

tb_train = TreeBank.from_file("./data/train.trees")
word_freq = tb_train.count_word_freq()
vocab_train = set(map(lambda x: x[0], filter(lambda x: x[1] > 1, word_freq.items())))
rare_train = set(map(lambda x: x[0], filter(lambda x: x[1] == 1, word_freq.items())))
#vocab_train = tb_train.collect_vocabulary()


tb_dev = TreeBank.from_file("./data/test.trees")
vocab_dev = tb_dev.collect_vocabulary()

diff = vocab_dev.difference(vocab_train)
for w in diff:
    print(w)

print(rare_train.intersection(vocab_dev))
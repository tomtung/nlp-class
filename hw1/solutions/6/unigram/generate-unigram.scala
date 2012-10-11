#!/bin/bash
exec scala "$0" "$@"
!#

val training_strings_path = "../training-strings"
val vocab_path = "../../../vocab"
val unigram_path = "./unigram.fsa"

val vocab = io.Source.fromFile(vocab_path).getLines().map(_.replaceAll(" ", "")).toSet

val lambda1: Double = 0.01

val unigramFrequency = io.Source.fromFile(training_strings_path).getLines().
  flatMap(_.split(" ")).
  filter(vocab.contains _).
  toList.
  groupBy(identity).
  mapValues(_.size).
  withDefaultValue(0)

val unigramFrequencySum = vocab.iterator.map(unigramFrequency).map(lambda1+).sum

def computeUnigramWeight(word: String): Double =
  (unigramFrequency(word) + lambda1) / unigramFrequencySum

val writer = new java.io.PrintWriter(unigram_path)

writer.println("S")
for((word, weight) <- vocab.iterator.map(w => w -> computeUnigramWeight(w)))
  writer.println("""(S (S "%s" %.3e))""".format(word, weight))

writer.close()

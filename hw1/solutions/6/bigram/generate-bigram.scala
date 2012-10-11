#!/bin/bash
exec scala "$0" "$@"
!#

val training_strings_path = "../training-strings"
val vocab_path = "../../../vocab"
val bigram_path = "./bigram.fsa"

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

val lambda2: Double = 0.05

val bigramFrequency = io.Source.fromFile(training_strings_path).getLines().
  map(_.trim.split(" ")).
  flatMap(l => l zip l.tail).
  filter({case(l, r) => vocab.contains(l) && vocab.contains(r)}).
  toList.
  groupBy(identity).
  mapValues(_.size).
  withDefaultValue(0)

def computeBigramWeight(lword: String, rword: String): Double = {
  val denominator = unigramFrequency(lword) + lambda2
  rword match {
    case "$" =>
      lambda2 / denominator
    case _ =>
      bigramFrequency(lword -> rword) / denominator
  }
}

// println(unigramFrequency("EVENING"))
// println(computeUnigramWeight("EVENING"))
// println("""($ (%s "%s" %.3e))""".format("EVENING", "EVENING", computeUnigramWeight("EVENING")));
// println(unigramFrequency("VINING"))
// println(computeUnigramWeight("VINING"))
// println("""($ (%s "%s" %.3e))""".format("VINING", "VINING", computeUnigramWeight("VINING")));
// println(computeBigramWeight("TOMORROW","EVENING"))
// println("""(%s (%s "%s" %.3e))""".format("TOMORROW", "EVENING", "EVENING", computeBigramWeight("TOMORROW", "EVENING")))
// println(computeBigramWeight("TOMORROW","VEINING"))
// println("""(%s (%s "%s" %.3e))""".format("TOMORROW", "VEINING", "VEINING", computeBigramWeight("TOMORROW", "VEINING")))
// sys.exit()


val writer = new java.io.PrintWriter(bigram_path)

writer.println("$")
for(word <- vocab){
  writer.println(
    """($ (%s "%s" %.3e))""".format(word, word, computeUnigramWeight(word)))
  writer.println(
    """(%s ($ *e* %.3e))""".format(word, computeBigramWeight(word, "$")))
}
for ((lword, rword) <- bigramFrequency.keys) {
  writer.println(
    """(%s (%s "%s" %.3e))""".format(lword, rword, rword, computeBigramWeight(lword, rword)))
}

writer.close()

val trainingCorpus = TaggedCorpus.fromFile("./train.tags")

println("Number of distinct words: " + trainingCorpus.wordToTags.size)

println("Number of distinct tags: " + trainingCorpus.tags.size)

println("Words with most distinct tags (3 or more): ")
for ((word, tags) <- trainingCorpus.wordToTags.toSeq.sortBy(-_._2.size).takeWhile(_._2.size >= 3)) {
  println("%s\t%s".format(word, tags.mkString(",")))
}

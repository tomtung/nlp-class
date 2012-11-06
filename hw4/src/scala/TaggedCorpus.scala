import collection.immutable.IndexedSeq

class TaggedCorpus(exampleIter: => Iterator[IndexedSeq[(String, String)]]) {

  val examples: List[IndexedSeq[(String, String)]] = exampleIter.toList

  val (wordToTags: Map[String, Set[String]], tags: Set[String]) = {
    val initWordToTags = Map[String, Set[String]]().withDefaultValue(Set[String]())
    val initTags = Set[String]()
    examples.flatten.map({
      // Case-insensitive
      case (w, t) => (w.toLowerCase, t)
    }).foldLeft((initWordToTags, initTags))({
      case ((w2ts, ts), (w, t)) =>
        val tsOfw = w2ts(w) + t
        (w2ts.updated(w, tsOfw), ts + t)
    })
  }

  val words = wordToTags.keySet

  val wordFreq = examples.flatten.groupBy(_._1.toLowerCase).mapValues(_.size).withDefaultValue(0)

  val tagsFreq = examples.flatten.groupBy(_._2).mapValues(_.size)
}

object TaggedCorpus {
  def fromFile(path: String) = {
    def examples = io.Source.fromFile(path).getLines().
      map(line =>
      line.trim.split("\\s+").map(s => {
        val a = s.split("_")
        (a(0), a(1))
      }).toIndexedSeq)

    new TaggedCorpus(examples)
  }
}
import collection.immutable.IndexedSeq

class TaggedCorpus(exampleIter: => Iterator[IndexedSeq[(String, String)]]) {

  val examples = exampleIter.toList

  val (wordToTags: Map[String, Set[String]], tags: Set[String]) = {
    val initWordToTags = Map[String, Set[String]]().withDefaultValue(Set[String]())
    val initTags = Set[String]()
    examples.flatten.map({case(w,t) => (w.toLowerCase, t)}). // Case-insensitive
      foldLeft((initWordToTags, initTags))({
      case ((w2ts, ts), (w, t)) =>
        val tsOfw = w2ts(w) + t
        (w2ts.updated(w, tsOfw), ts + t)
    })
  }

  val words = wordToTags.keySet
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
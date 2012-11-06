val trainCorpus = TaggedCorpus.fromFile("./train.tags")
val devCorpus = TaggedCorpus.fromFile("./dev.tags")
val testCorpus = TaggedCorpus.fromFile("./test.tags")

def computeAccuracy(taggedExamples: List[IndexedSeq[(String, String)]], model: PosTagger): Double = {
  val n = taggedExamples.flatten.size.asInstanceOf[Double]

  taggedExamples.map(example => {
    model.computePosTag(example.map(_._1)).zip(example.map(_._2)).count({
      case (t1, t2) => t1 == t2
    })
  }).sum / n
}

def isConverged(nIter: => Int, prevL: => Double, L: => Double, model: PosTagger) = {
  Console.err.println("Iteration " + nIter + " completed. Lost = " + L)

  println("Train accuracy: " + computeAccuracy(trainCorpus.examples, model))
  println("Dev accuracy: " + computeAccuracy(devCorpus.examples, model))
  println("Test accuracy: " + computeAccuracy(testCorpus.examples, model))

  val trainWriter = new java.io.PrintWriter("./output/train.output" + nIter)
  trainCorpus.examples.map(_.map(_._1)).foreach(input => {
    val output = (input zip model.computePosTag(input)).map({
      case (w, t) => w + "_" + t
    }).mkString(" ")
    trainWriter.println(output)
  })
  trainWriter.close()

  val devWriter = new java.io.PrintWriter("./output/dev.output" + nIter)
  io.Source.fromFile("./dev").getLines().map(_.trim.split("\\s+").toIndexedSeq).foreach(input => {
    val output = (input zip model.computePosTag(input)).map({
      case (w, t) => w + "_" + t
    }).mkString(" ")
    devWriter.println(output)
  })
  devWriter.close()

  val testWriter = new java.io.PrintWriter("./output/test.output" + nIter)
  io.Source.fromFile("./test").getLines().map(_.trim.split("\\s+").toIndexedSeq).foreach(input => {
    val output = (input zip model.computePosTag(input)).map({
      case (w, t) => w + "_" + t
    }).mkString(" ")
    testWriter.println(output)
  })
  testWriter.close()

  nIter > 50
}

val prefixSet = io.Source.fromFile("./prefix").getLines().toSet
val suffixSet = io.Source.fromFile("./suffix").getLines().toSet

val (model, _) = CrfPosTagger.trainFromCorpus(
  trainCorpus, prefixSet, suffixSet, 0.3, 0, isConverged, new util.Random(0)
)

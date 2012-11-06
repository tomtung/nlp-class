import breeze.linalg.{CSCMatrix, DenseMatrix, Matrix}

class BasicCrfPosTagger(words: Set[String],
                        tags: Set[String],
                        wordToPossibleTagNames: String => Set[String],
                        random: util.Random = util.Random) extends CrfPosTaggerBase(tags, random) {
  final val wordList: IndexedSeq[String] = words.map(_.toLowerCase).toIndexedSeq

  protected final val wordToIndex: (String) => Int =
    ((f: String => Int) => (word: String) => if (word == null) f(word) else f(word.toLowerCase))(
      wordList.view.zipWithIndex.toMap.withDefaultValue(-1).apply _
    )

  override final def wordToPossibleTags(word: String): Set[Int] = {
    wordToPossibleTagNames(word).map(tagToIndex)
  }

  protected final val λtt: Matrix[Double] = {
    val m = DenseMatrix.zeros[Double](tagList.size, tagList.size)
    m(0, 0) = Double.NaN
    m
  }

  protected final val λtw: Matrix[Double] = {
    val m = DenseMatrix.zeros[Double](tagList.size, wordList.size)
    for (w <- 0 until wordList.size)
      m(0, w) = Double.NaN
    m
  }

  private final var Δλtw: CSCMatrix[Double] = null
  private final var Δλtt: CSCMatrix[Double] = null

  override def weightOnEdge(prevTag: Int, tag: Int, word: String) = {
    val w = wordToIndex(word)

    if (tag == 0 || w == -1) λtt(prevTag, tag)
    else λtt(prevTag, tag) + λtw(tag, w)
  }

  override def prepareParamDelta() {
    Δλtw = CSCMatrix.zeros[Double](tagList.size, wordList.size)
    Δλtt = CSCMatrix.zeros[Double](tagList.size, tagList.size)
  }

  override def updateParamDelta(prevTag: Int, tag: Int, word: String, factor: Double) {
    Δλtt(prevTag, tag) += factor
    if (word != null) {
      val w = wordToIndex(word)
      Δλtw(tag, w) += factor
    }
  }

  override def applyParamDelta(learningRate: Double) {
    Δλtw.activeIterator.foreach({
      case ((t, w), d) =>
        λtw(t, w) += learningRate * d
    })
    Δλtw = null

    Δλtt.activeIterator.foreach({
      case ((t1, t2), d) =>
        λtt(t1, t2) += learningRate * d
    })
    Δλtt = null
  }

  override def updateAllParams(f: Double => Double) {
    for (w <- 0 until wordList.size;
         t <- wordToPossibleTags(wordList(w)))
      λtw(t, w) = f(λtw(t, w))
    for (t1 <- 0 until tagList.size;
         t2 <- 0 until tagList.size)
      λtt(t1, t2) = f(λtt(t1, t2))
  }

  override def allParamIter: Iterator[Double] =
    λtt.valuesIterator.drop(1) ++ // (0,0) is dropped
      wordList.iterator.flatMap(word => wordToPossibleTags(word).map(_ -> wordToIndex(word))).map({
        case (t, w) => λtw(t, w)
      })
}

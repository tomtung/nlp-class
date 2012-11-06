import breeze.linalg._
import collection.immutable.IndexedSeq
import collection.mutable

object CrfPosTagger {
  def trainFromCorpus(corpus: TaggedCorpus,
                      prefixSet: Set[String],
                      suffixSet: Set[String],
                      learningRate: Double,
                      regParam: Double,
                      isConverged: (=> Int, => Double, => Double, PosTagger) => Boolean,
                      random: util.Random = util.Random): (PosTagger, Double) = {
    val mostFrequentTags = corpus.tagsFreq.toList.sortBy(-_._2).map(_._1).take(3).toSet
    def wordToPossibleTags(word: String): Set[String] = {
      corpus.wordFreq(word) match {
        case 0 => corpus.tags
        case x if x < 2 => corpus.wordToTags(word) union mostFrequentTags
        case _ => corpus.wordToTags(word)
      }
    }
    val model = new CrfPosTaggerImpl(
      corpus.words, prefixSet, suffixSet,
      corpus.tags, wordToPossibleTags,
      random)
    val L = model.train(learningRate, regParam, isConverged)(corpus.examples)

    (model, L)
  }

  // A lot of ugly and redundant code. No time to refactor...
  private class CrfPosTaggerImpl(words: Set[String], prefixes: Set[String], suffixes: Set[String],
                                 tags: Set[String],
                                 wordToPossibleTagNames: String => Set[String],
                                 random: util.Random = util.Random) extends BasicCrfPosTagger(words, tags, wordToPossibleTagNames, random) {

    // Prefix

    private final val prefixList: IndexedSeq[String] = prefixes.toList.sortBy(-_.length).toIndexedSeq

    protected final val λ_prefix_t: Matrix[Double] = {
      val m = DenseMatrix.tabulate[Double](prefixList.size, tagList.size)(
        (_, t) => if (t == 0) Double.NaN else 0
      )
      m
    }

    private final def prefixParamsIter = {
      for (p <- 0 until prefixList.size; t <- 1 until tagList.size)
      yield λ_prefix_t(p, t)
    }.iterator

    private final var Δλ_prefix_t: CSCMatrix[Double] = null

    private final def prepareParamDeltaPrefix() {
      Δλ_prefix_t = CSCMatrix.zeros[Double](prefixList.size, tagList.size)
    }

    private final def applyParamDeltaPrefix(learningRate: Double) {
      Δλ_prefix_t.activeIterator.foreach({
        case ((p, t), d) =>
          λ_prefix_t(p, t) += learningRate * d
      })
      Δλ_prefix_t = null
    }

    private final def updateAllParamsPrefix(f: (Double) => Double) {
      for (p <- 0 until prefixList.size; t <- 1 until tagList.size)
        λ_prefix_t(p, t) = f(λ_prefix_t(p, t))
    }

    private final def weightOnEdgePrefix(prevTag: Int, tag: Int, word: String): Double = {
      val i = findPrefixIndex(word)
      if (i >= 0) λ_prefix_t(i, tag)
      else 0
    }

    val wordToPrefixIndex = mutable.Map[String, Int]()

    def findPrefixIndex(word: String): Int = {
      if (word == null) -1
      else wordToPrefixIndex.getOrElseUpdate(word.toLowerCase,
        prefixList.indexWhere(p => word.length >= p.length + 2 && word.toLowerCase.startsWith(p)))
    }

    private final def updateParamDeltaPrefix(prevTag: Int, tag: Int, word: String, factor: Double) {
      val i = findPrefixIndex(word)
      if (i >= 0)
        Δλ_prefix_t(i, tag) += factor
    }


    // Suffix

    private final val suffixList: IndexedSeq[String] = suffixes.toList.sortBy(-_.length).toIndexedSeq

    protected final val λ_suffix_t: Matrix[Double] = {
      val m = DenseMatrix.tabulate[Double](suffixList.size, tagList.size)(
        (_, t) => if (t == 0) Double.NaN else 0
      )
      m
    }

    private final def suffixParamsIter = {
      for (s <- 0 until suffixList.size; t <- 1 until tagList.size)
      yield λ_suffix_t(s, t)
    }.iterator

    private final var Δλ_suffix_t: CSCMatrix[Double] = null

    private final def prepareParamDeltaSuffix() {
      Δλ_suffix_t = CSCMatrix.zeros[Double](suffixList.size, tagList.size)
    }

    private final def applyParamDeltaSuffix(learningRate: Double) {
      Δλ_suffix_t.activeIterator.foreach({
        case ((s, t), d) =>
          λ_suffix_t(s, t) += learningRate * d
      })
      Δλ_suffix_t = null
    }

    private final def updateAllParamsSuffix(f: (Double) => Double) {
      for (s <- 0 until suffixList.size; t <- 1 until tagList.size)
        λ_suffix_t(s, t) = f(λ_suffix_t(s, t))
    }

    private final def weightOnEdgeSuffix(prevTag: Int, tag: Int, word: String): Double = {
      val i = findSuffixIndex(word)
      if (i >= 0) λ_suffix_t(i, tag)
      else 0
    }

    val wordToSuffixIndex = mutable.Map[String, Int]()

    def findSuffixIndex(word: String): Int = {
      if (word == null) -1
      else wordToSuffixIndex.getOrElseUpdate(word.toLowerCase,
        suffixList.indexWhere(p => word.length >= p.length + 2 && word.toLowerCase.endsWith(p)))
    }

    private final def updateParamDeltaSuffix(prevTag: Int, tag: Int, word: String, factor: Double) {
      val i = findSuffixIndex(word)
      if (i >= 0)
        Δλ_suffix_t(i, tag) += factor
    }


    // 1st Upper
    private final val λ_1stUpper_t = {
      val v = DenseVector.zeros[Double](tagList.size)
      v(0) = Double.NaN
      v
    }

    private final def is1stUpperParamsIter = λ_1stUpper_t.valuesIterator.drop(1)

    private final var Δλ_1stUpper_t: SparseVector[Double] = null

    private final def prepareParamDelta1stUpper() {
      Δλ_1stUpper_t = SparseVector.zeros[Double](tagList.size)
    }

    def applyParamDelta1stUpper(learningRate: Double) {
      Δλ_1stUpper_t :*= learningRate
      λ_1stUpper_t += Δλ_1stUpper_t

      Δλ_1stUpper_t = null
    }

    def updateAllParams1stUpper(f: (Double) => Double) {
      for (tag <- 1 until tagList.size)
        λ_1stUpper_t(tag) = f(λ_1stUpper_t(tag))
    }

    def weightOnEdge1stUpper(prevTag: Int, tag: Int, word: String): Double = {
      if (is1stUpper(prevTag, tag, word)) λ_1stUpper_t(tag)
      else 0
    }

    private final def is1stUpper(prevTag: Int, tag: Int, word: String) =
      word != null && prevTag != 0 && word.head.isUpper

    private final def updateParamDelta1stUpper(prevTag: Int, tag: Int, word: String, factor: Double) {
      if (is1stUpper(prevTag, tag, word)) {
        Δλ_1stUpper_t(tag) += factor
      }
    }


    // All-digit
    private final val λ_allDigit_t = {
      val v = DenseVector.zeros[Double](tagList.size)
      v(0) = Double.NaN
      v
    }

    private final def allDigitParamsIter = λ_allDigit_t.valuesIterator.drop(1)

    private final var Δλ_allDigit_t: SparseVector[Double] = null

    def prepareParamDeltaAllDigit() {
      Δλ_allDigit_t = SparseVector.zeros[Double](tagList.size)
    }

    def applyParamDeltaAllDigit(learningRate: Double) {
      Δλ_allDigit_t :*= learningRate
      λ_allDigit_t += Δλ_allDigit_t

      Δλ_allDigit_t = null
    }

    def updateAllParamsAllDigit(f: (Double) => Double) {
      for (tag <- 1 until tagList.size)
        λ_allDigit_t(tag) = f(λ_allDigit_t(tag))
    }

    def weightOnEdgeAllDigit(prevTag: Int, tag: Int, word: String): Double = {
      if (isAllDigit(prevTag, tag, word)) λ_allDigit_t(tag)
      else 0
    }

    private def isAllDigit(prevTag: Int, tag: Int, word: String) =
      word != null && word.exists(_.isDigit) && word.forall(c => !c.isLetter)

    private final def updateParamDeltaAllDigit(prevTag: Int, tag: Int, word: String, factor: Double) {
      if (isAllDigit(prevTag, tag, word)) {
        Δλ_allDigit_t(tag) += factor
      }
    }

    // Overrides

    override def allParamIter =
      super.allParamIter ++
        allDigitParamsIter ++
        is1stUpperParamsIter ++
        prefixParamsIter ++
        suffixParamsIter

    override def prepareParamDelta() {
      super.prepareParamDelta()
      prepareParamDeltaAllDigit()
      prepareParamDelta1stUpper()
      prepareParamDeltaPrefix()
      prepareParamDeltaSuffix()
    }


    override def updateParamDelta(prevTag: Int, tag: Int, word: String, factor: Double) {
      super.updateParamDelta(prevTag, tag, word, factor)
      updateParamDeltaAllDigit(prevTag, tag, word, factor)
      updateParamDelta1stUpper(prevTag, tag, word, factor)
      updateParamDeltaPrefix(prevTag, tag, word, factor)
      updateParamDeltaSuffix(prevTag, tag, word, factor)
    }


    override def applyParamDelta(learningRate: Double) {
      super.applyParamDelta(learningRate)
      applyParamDeltaAllDigit(learningRate)
      applyParamDelta1stUpper(learningRate)
      applyParamDeltaPrefix(learningRate)
      applyParamDeltaSuffix(learningRate)
    }


    override def updateAllParams(f: (Double) => Double) {
      super.updateAllParams(f)
      updateAllParamsAllDigit(f)
      updateAllParams1stUpper(f)
      updateAllParamsPrefix(f)
      updateAllParamsSuffix(f)
    }


    override def weightOnEdge(prevTag: Int, tag: Int, word: String) = {
      super.weightOnEdge(prevTag, tag, word) +
        weightOnEdgeAllDigit(prevTag, tag, word) +
        weightOnEdge1stUpper(prevTag, tag, word) +
        weightOnEdgePrefix(prevTag, tag, word) +
        weightOnEdgeSuffix(prevTag, tag, word)

    }

  }

}

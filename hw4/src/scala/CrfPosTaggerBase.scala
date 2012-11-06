import breeze.linalg.{DenseMatrix, Matrix}

abstract class CrfPosTaggerBase(val tags: Set[String], random: util.Random = util.Random) extends PosTagger {
  protected final val tagList: IndexedSeq[String] = null +: tags.toIndexedSeq

  protected final val tagToIndex: Map[String, Int] = tagList.view.zipWithIndex.toMap

  protected def wordToPossibleTags(word: String): Set[Int]

  protected def weightOnEdge(prevTag: Int, tag: Int, word: String): Double

  protected def allParamIter: Iterator[Double]

  protected def prepareParamDelta(): Unit

  protected def updateParamDelta(prevTag: Int, tag: Int, word: String, factor: Double): Unit

  protected def applyParamDelta(learningRate: Double): Unit

  protected def updateAllParams(f: Double => Double): Unit

  protected final def computeForward(example: IndexedSeq[String]): (Matrix[Double], Double) = {
    val α = DenseMatrix.fill[Double](example.size, tagList.size)(Double.NaN)

    for (i <- 0 until example.size; tg <- wordToPossibleTags(example(i)).iterator) {
      α(i, tg) =
        if (i == 0)
          weightOnEdge(0, tg, example.head)
        else
          wordToPossibleTags(example(i - 1)).iterator.map(
            prevTg => α(i - 1, prevTg) + weightOnEdge(prevTg, tg, example(i))
          ).reduce(Util.logPlus)
    }

    val α_end = wordToPossibleTags(example.last).iterator.map(
      tg => α(example.size - 1, tg) + weightOnEdge(tg, 0, null)
    ).reduce(Util.logPlus)

    (α, α_end)
  }

  protected final def computeBackward(example: IndexedSeq[String]): (Matrix[Double], Double) = {
    val β = DenseMatrix.fill[Double](example.size, tagList.size)(Double.NaN)

    for (i <- (example.size - 1) to 0 by -1; tg <- wordToPossibleTags(example(i)).iterator) {
      β(i, tg) =
        if (i == example.size - 1)
          weightOnEdge(tg, 0, null)
        else wordToPossibleTags(example(i + 1)).iterator.map(
          nextTg => weightOnEdge(tg, nextTg, example(i + 1)) + β(i + 1, nextTg)
        ).reduce(Util.logPlus)
    }

    val β_start = wordToPossibleTags(example.head).iterator.map(
      tg => weightOnEdge(0, tg, example.head) + β(0, tg)
    ).reduce(Util.logPlus)

    (β, β_start)
  }

  private def computeExampleCost(example: IndexedSeq[(String, Int)]): Double = {
    val energy = weightOnEdge(0, example.head._2, example.head._1) +
      (1 until example.size).iterator.map(
        i => weightOnEdge(example(i - 1)._2, example(i)._2, example(i)._1)
      ).sum +
      weightOnEdge(example.last._2, 0, null)

    val (_, logZ) = computeForward(example.map(_._1))

    // Although when updating on each example, regularization is performed each time,
    // when computing cost, regularization is only done to the entire training set
    -(energy - logZ)
  }

  private def trainOnExample(learningRate: Double, regParam: Double)(example: IndexedSeq[(String, Int)]) {
    prepareParamDelta()

    (0 +: example.map(_._2), example.map(_._2) :+ 0, example.map(_._1) :+ null).zipped.foreach({
      case (prevTag, tag, word) =>
        updateParamDelta(prevTag, tag, word, 1)
    })

    val (α, α_end) = computeForward(example.map(_._1))
    val (β, β_start) = computeBackward(example.map(_._1))
    //      assert(math.abs(α_end - β_start) <= math.abs(0.1 * α_end), α_end + " " + β_start)

    for (i <- 1 until example.size) {
      for (prevTag <- wordToPossibleTags(example(i - 1)._1).iterator;
           tag <- wordToPossibleTags(example(i)._1).iterator) {
        val lgE = α(i - 1, prevTag) + weightOnEdge(prevTag, tag, example(i)._1) + β(i, tag) - α_end
        updateParamDelta(prevTag, tag, example(i)._1, -math.exp(lgE))
      }
    }

    for (tag <- wordToPossibleTags(example(0)._1).iterator) {
      val lgE = weightOnEdge(0, tag, example(0)._1) + β(0, tag) - α_end
      updateParamDelta(0, tag, example(0)._1, -math.exp(lgE))
    }
    for (prevTag <- wordToPossibleTags(example(example.size - 1)._1).iterator) {
      val lgE = α(example.size - 1, prevTag) + weightOnEdge(prevTag, 0, null) - α_end
      updateParamDelta(prevTag, 0, null, -math.exp(lgE))
    }

    assert(regParam * learningRate < 1)
    updateAllParams(x => x * (1 - regParam * learningRate))

    applyParamDelta(learningRate)
  }

  final def train(learningRate: Double,
                  regParam: Double,
                  isConverged: (=> Int, => Double, => Double) => Boolean)
                 (trainExamples: List[IndexedSeq[(String, String)]]): Double = {
    val indexedExamples = trainExamples.map(_.map({
      case (w, t) => (w, tagToIndex(t))
    }))

    def doTrain(examples: List[IndexedSeq[(String, Int)]],
                nIter: Int,
                prevL: Double): Double = {

      examples.foreach(trainOnExample(learningRate, regParam))

      val regTerm = regParam / 2 * allParamIter.map(x => x * x).sum

      val L = indexedExamples.map(computeExampleCost).sum + regTerm
      if (isConverged(nIter, prevL, L)) L
      else doTrain(util.Random.shuffle(indexedExamples), nIter + 1, L)
    }

    doTrain(indexedExamples, 0, Double.NegativeInfinity)
  }

  final def computePosTag(input: IndexedSeq[String]) = {
    val logMaxEnergy = DenseMatrix.fill[Double](input.size, tagList.size)(Double.NaN)
    val bestTagPointer = DenseMatrix.fill[Int](input.size, tagList.size)(-1)

    for (i <- 0 until input.size;
         tag <- wordToPossibleTags(input(i)).iterator) {
      if (i == 0) {
        logMaxEnergy(i, tag) = weightOnEdge(0, tag, input(i))
      }
      else {
        val (currMaxP, currMaxE) = wordToPossibleTags(input(i - 1)).iterator.map(
          prevTag => prevTag -> (logMaxEnergy(i - 1, prevTag) + weightOnEdge(prevTag, tag, input(i)))
        ).maxBy(_._2)
        logMaxEnergy(i, tag) = currMaxE
        bestTagPointer(i, tag) = currMaxP
      }
    }

    val (bestLastTag, _) = wordToPossibleTags(input.last).iterator.map(
      lastTag => lastTag -> logMaxEnergy(input.size - 1, lastTag)
    ).maxBy(_._2)

    def buildPath(i: Int, tag: Int, accu: List[Int] = Nil): IndexedSeq[Int] =
      if (i == 0) (tag :: accu).toIndexedSeq
      else buildPath(i - 1, bestTagPointer(i, tag), tag :: accu)

    buildPath(input.size - 1, bestLastTag).map(tagList)
  }
}

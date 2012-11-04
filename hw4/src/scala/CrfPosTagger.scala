import breeze.linalg.{DenseMatrix, Matrix, CSCMatrix}

object CrfPosTagger {

  def trainFromCorpus(corpus: TaggedCorpus,
                      learningRate: Double,
                      regParam: Double,
                      isConverged: (=> Int, => Double, => Double) => Boolean): (PosTagger, Double) = {
    val model = new BasicCrfPosTagger(corpus.words, corpus.tags, corpus.wordToTags)
    val L = model.train(learningRate, regParam, isConverged)(corpus.examples)

    (model, L)
  }

  private class BasicCrfPosTagger(val words: Set[String],
                                  val tags: Set[String],
                                  val wordToTags: Map[String, Set[String]]) extends PosTagger {
    val wordList: IndexedSeq[String] = words.toIndexedSeq

    val tagList: IndexedSeq[String] = null +: tags.toIndexedSeq

    val wordToIndex = wordList.view.zipWithIndex.toMap

    val tagToIndex = tagList.view.zipWithIndex.toMap

    val wordToPossibleTagIndices = wordToTags.map({
      case (w, t) => w -> t.map(tagToIndex)
    }).withDefaultValue((1 to tags.size).toSet)

    val wordIndexToPossibleTagIndices = wordToTags.map({
      case (w, t) => wordToIndex(w) -> t.map(tagToIndex)
    }).withDefaultValue((1 to tags.size).toSet)

    // TODO generalize below to other features

    val λtt: Matrix[Double] = {
      val m = DenseMatrix.zeros[Double](tagList.size, tagList.size)
      m(0, 0) = Double.NaN
      m
    }

    val λtw: Matrix[Double] = {
      val m = DenseMatrix.zeros[Double](tagList.size, wordList.size)
      for (w <- 0 until words.size)
        m(0, w) = Double.NaN
      m
    }

    def computeForward(example: IndexedSeq[Int]): (Matrix[Double], Double) = {
      // TODO what if the words are not seen before? How are other features (e.g. start with capital) represented?
      val α = DenseMatrix.fill[Double](example.size, tagList.size)(Double.NaN)

      for (i <- 0 until example.size; tg <- wordIndexToPossibleTagIndices(example(i))) {
        α(i, tg) =
          if (i == 0)
            weightOnEdge(0, tg, example.head)
          else
            wordIndexToPossibleTagIndices(example(i - 1)).map(
              prevTg => α(i - 1, prevTg) + weightOnEdge(prevTg, tg, example(i))
            ).reduce(Util.logPlus)
      }

      val α_end = wordIndexToPossibleTagIndices(example.last).map(
        tg => α(example.size - 1, tg) + weightOnEdge(tg, 0, -1)
      ).reduce(Util.logPlus)

      (α, α_end)
    }

    def computeBackward(example: IndexedSeq[Int]): (Matrix[Double], Double) = {
      val β = DenseMatrix.fill[Double](example.size, tagList.size)(Double.NaN)

      for (i <- (example.size - 1) to 0 by -1; tg <- wordIndexToPossibleTagIndices(example(i))) {
        β(i, tg) =
          if (i == example.size - 1)
            weightOnEdge(tg, 0, -1)
          else wordIndexToPossibleTagIndices(example(i + 1)).map(
            nextTg => weightOnEdge(tg, nextTg, example(i + 1)) + β(i + 1, nextTg)
          ).reduce(Util.logPlus)
      }

      val β_start = wordIndexToPossibleTagIndices(example.head).map(
        tg => weightOnEdge(0, tg, example.head) + β(0, tg)
      ).reduce(Util.logPlus)

      (β, β_start)
    }


    def tagBigrams(example: IndexedSeq[(Int, Int)]) = (0 +: example.map(_._2)) zip (example.map(_._2) :+ 0)

    def computeExampleCost(example: IndexedSeq[(Int, Int)]): Double = {
      val energy = weightOnEdge(0, example.head._2, example.head._1) +
        (1 until example.size).iterator.map(
          i => weightOnEdge(example(i - 1)._2, example(i)._2, example(i)._1)
        ).sum +
        weightOnEdge(example.last._2, 0, -1)

      val (_, logZ) = computeForward(example.map(_._1))

      // Although when updating on each example, regularization is performed each time,
      // when computing cost, regularization is only done to the entire training set
      -(energy - logZ)
    }

    def weightOnEdge(prevTag: Int, tag: Int, w: Int) = {
      if (tag == 0 || w < 0) λtt(prevTag, tag)
      else λtt(prevTag, tag) + λtw(tag, w)
    }

    def trainOnExample(learningRate: Double, regParam: Double)(example: IndexedSeq[(Int, Int)]) {
      val Δλtw = CSCMatrix.zeros[Double](tagList.size, wordList.size)
      val Δλtt = CSCMatrix.zeros[Double](tagList.size, tagList.size)

      example.iterator.foreach({
        case (w, t) => Δλtw(t, w) += 1
      })
      tagBigrams(example).iterator.foreach({
        case (t1, t2) => Δλtt(t1, t2) += 1
      })

      val (α, α_end) = computeForward(example.map(_._1))
      val (β, β_start) = computeBackward(example.map(_._1))
      if (math.abs(α_end - β_start) > 0.1) println(α_end + " " + β_start)
      //      assert(math.abs(α_end - β_start) < 0.1, α_end + " " + β_start)

      // All features are defined on (word, prevTag, tag)
      for (i <- 1 until example.size;
           prevTag <- wordIndexToPossibleTagIndices(example(i - 1)._1);
           tag <- wordIndexToPossibleTagIndices(example(i)._1)) {
        val lgE = α(i - 1, prevTag) + weightOnEdge(prevTag, tag, example(i)._1) + β(i, tag) - α_end
        Δλtt(prevTag, tag) -= math.exp(lgE)
        Δλtw(tag, example(i)._1) -= math.exp(lgE)
      }
      for (tag <- wordIndexToPossibleTagIndices(example(0)._1)) {
        val lgE = weightOnEdge(0, tag, example(0)._1) + β(0, tag) - α_end
        Δλtt(0, tag) -= math.exp(lgE)
        Δλtw(tag, example(0)._1) -= math.exp(lgE)
      }
      for (prevTag <- wordIndexToPossibleTagIndices(example(example.size - 1)._1)) {
        val lgE = α(example.size - 1, prevTag) + weightOnEdge(prevTag, 0, -1) - α_end
        Δλtt(prevTag, 0) -= math.exp(lgE)
      }

      assert(regParam * learningRate < 1)
      for (w <- 0 until wordList.size;
           t <- wordIndexToPossibleTagIndices(w))
        λtw(t, w) *= 1 - regParam * learningRate
      for (t1 <- 0 until tagList.size;
           t2 <- 0 until tagList.size)
        λtt(t1, t2) *= 1 - regParam * learningRate

      Δλtw.activeIterator.foreach({
        case ((t, w), d) =>
          λtw(t, w) += learningRate * d
      })

      Δλtt.activeIterator.foreach({
        case ((t1, t2), d) =>
          λtt(t1, t2) += learningRate * d
      })
    }

    def allParamIter: Iterator[Double] =
      λtt.valuesIterator.drop(1) ++ // (0,0) is dropped
        (0 until wordList.size).iterator.flatMap(w => wordIndexToPossibleTagIndices(w).map(_ -> w)).map({
          case (t, w) => λtw(t, w)
        })

    def train(learningRate: Double,
              regParam: Double,
              isConverged: (=> Int, => Double, => Double) => Boolean)
             (trainExamples: List[IndexedSeq[(String, String)]]): Double = {
      val indexedExamples = trainExamples.map(_.map({
        case (w, t) => (wordToIndex(w.toLowerCase), tagToIndex(t)) // TODO handle case
      }))

      def doTrain(examples: List[IndexedSeq[(Int, Int)]],
                  nIter: Int,
                  prevL: Double): Double = {

        examples.foreach(trainOnExample(learningRate, regParam))

        val regTerm = regParam / 2 * allParamIter.map(x => x * x).sum

        println(regTerm)
        val L = indexedExamples.map(computeExampleCost).sum + regTerm
        if (isConverged(nIter, prevL, L)) L
        else doTrain(util.Random.shuffle(indexedExamples), nIter + 1, L)
      }

      doTrain(indexedExamples, 0, Double.NegativeInfinity)
    }

    def computePosTag(input: IndexedSeq[String]) = {
      val lowerInput = input.map(_.toLowerCase) // TODO make this general
      val logMaxEnergy = DenseMatrix.fill[Double](lowerInput.size, tagList.size)(Double.NaN)
      val bestTagPointer = DenseMatrix.fill[Int](lowerInput.size, tagList.size)(-1)

      for (i <- 0 until lowerInput.size;
           tag <- wordToPossibleTagIndices(lowerInput(i))) {
        val w =
          if (words.contains(lowerInput(i)))
            wordToIndex(lowerInput(i))
          else -1

        if (i == 0) {
          logMaxEnergy(i, tag) = weightOnEdge(0, tag, w)
        }
        else {
          val (currMaxP, currMaxE) = wordToPossibleTagIndices(lowerInput(i - 1)).map(
            prevTag => prevTag -> (logMaxEnergy(i - 1, prevTag) + weightOnEdge(prevTag, tag, w))
          ).maxBy(_._2)
          logMaxEnergy(i, tag) = currMaxE
          bestTagPointer(i, tag) = currMaxP
        }
      }

      val (bestLastTag, _) = wordToPossibleTagIndices(lowerInput.last).map(
        lastTag => lastTag -> logMaxEnergy(lowerInput.size - 1, lastTag)
      ).maxBy(_._2)

      def buildPath(i: Int, tag: Int, accu: List[Int] = Nil): IndexedSeq[Int] =
        if (i == 0) (tag :: accu).toIndexedSeq
        else buildPath(i - 1, bestTagPointer(i, tag), tag :: accu)

      buildPath(lowerInput.size - 1, bestLastTag).map(tagList)
    }
  }

}

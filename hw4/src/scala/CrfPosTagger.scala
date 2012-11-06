import breeze.linalg.{SparseVector, DenseVector}

object CrfPosTagger {
  def trainFromCorpus(corpus: TaggedCorpus,
                      learningRate: Double,
                      regParam: Double,
                      isConverged: (=> Int, => Double, => Double) => Boolean,
                      random: util.Random = util.Random): (PosTagger, Double) = {
    //    val model = new CrfPosTaggerImpl(corpus.words, corpus.tags, corpus.wordToTags, random)
    val model = new BasicCrfPosTagger(corpus.words, corpus.tags, corpus.wordToTags, random)
    val L = model.train(learningRate, regParam, isConverged)(corpus.examples)

    (model, L)
  }

//  private class CrfPosTaggerImpl(words: Set[String],
//                                 tags: Set[String],
//                                 wordToTagsName: Map[String, Set[String]],
//                                 random: util.Random = util.Random) extends BasicCrfPosTagger(words, tags, wordToTagsName, random) {
//
//    //    private final val λ_unseenNNP_t = {
//    //      val v = DenseVector.zeros[Double](tagList.size)
//    //      v(0) = Double.NaN
//    //      v
//    //    }
//
//    //    private final var Δλ_unseenNNP_t: SparseVector[Double] = null
//
//    //    private def isUnseenNNP(word: String) =
//    //      word != null && word.length > 1 && word.exists(_.isLetter) &&
//    //        !words.contains(word.toLowerCase) &&
//    //        (word.filter(_.isLetter).forall(_.isUpper) ||
//    //          word.head.isUpper ||
//    //          word.endsWith("."))
//
//    private final val λ_allDigit_t = {
//      val v = DenseVector.zeros[Double](tagList.size)
//      v(0) = Double.NaN
//      v
//    }
//
//    private final var Δλ_allDigit_t: SparseVector[Double] = null
//
//    private def isAllDigit(word: String) = word != null && word.forall(c => c.isDigit || c == ',' || c == '.') && word.exists(_.isDigit)
//
//    override def allParamIter = super.allParamIter ++ λ_allDigit_t.valuesIterator.drop(1)
//
//    override def prepareParamDelta() {
//      super.prepareParamDelta()
//      Δλ_allDigit_t = SparseVector.zeros[Double](tagList.size)
//    }
//
//    override def updateParamDelta(prevTag: Int, tag: Int, word: String, factor: Double) {
//      super.updateParamDelta(prevTag, tag, word, factor)
//      if (isAllDigit(word)) {
//        Δλ_allDigit_t(tag) += factor
//      }
//    }
//
//    override def applyParamDelta(learningRate: Double) {
//      super.applyParamDelta(learningRate)
//
//
//      Δλ_allDigit_t :*= learningRate
//      λ_allDigit_t += Δλ_allDigit_t
//
//      Δλ_allDigit_t = null
//    }
//
//    override def updateAllParams(f: (Double) => Double) {
//      super.updateAllParams(f)
//      for (tag <- 1 until tagList.size)
//        λ_allDigit_t(tag) = f(λ_allDigit_t(tag))
//    }
//
//    override def weightOnEdge(prevTag: Int, tag: Int, word: String) = {
//      super.weightOnEdge(prevTag, tag, word) + {
//        if (isAllDigit(word)) λ_allDigit_t(tag)
//        else 0
//      }
//    }
//  }

}

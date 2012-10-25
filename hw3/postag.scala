import collection.mutable

/**
 * Compute log of base 2
 */
def log2(d: Double) = math.log(d) / math.log(2.0)

/**
 * Given l1 = log(x), l2 = log(y), compute the approximate value of log(x+y)
 */
def logPlus(l1: Double, l2: Double) = {
  assert(!l1.isNaN && !l2.isNaN)
  val l_larger = math.max(l1, l2)
  val l_smaller = math.min(l1, l2)
  if (l_smaller.isNegInfinity) l_larger
  else if (l_larger - l_smaller > 32) l_larger
  else l_larger + log2(1 + math.pow(2, l_smaller - l_larger))
}

/**
 * Normalize the given log vector, so that 2 to the power of each element sum up to 1.
 */
def normalizeLog(log_vec: IndexedSeq[Double]) = {
  val log_sum = log_vec.reduce(logPlus)
  log_vec.map(_ - log_sum)
}

/**
 * Create a random double Vector whose elements are positive and sum up to 1
 */
def randomLogProbVector(size: Int) =
  normalizeLog(IndexedSeq.fill(size)(util.Random.nextDouble()))

/**
 * Create a random Vector[Vector[Double]], each row of which sum up to 1
 */
def randomProbLogMatrix(nRows: Int, nColumns: Int) =
  IndexedSeq.fill(nRows)(randomLogProbVector(nColumns))

/**
 * Parameters for part-of-speech tagging
 * @param words The vocabulary.
 *              The index of each word corresponds to the word index in b
 * @param nTag Number of distinct tags
 * @param t t[tag1, tag2] = log P(tag2|tag1), i.e. bi-gram model
 * @param b b[tag, word] = log P(word|tag)
 */
case class Model(words: IndexedSeq[String], nTag: Int,
                 t: IndexedSeq[IndexedSeq[Double]],
                 b: IndexedSeq[IndexedSeq[Double]]) {
  assert(words.distinct.size == words.size)

  lazy val wordToIndex = words.zipWithIndex.toMap

  /**
   * Tags are indexed from 1 to nTag.
   * 0 represents the start and the end of a tag sequence.
   */
  val tags = 1 to nTag

  def params = (words, nTag, t, b)
}

object Model {
  /**
   * Create a model with random parameters for random restarts.
   */
  def newRandomModel(words: IndexedSeq[String], nTag: Int) =
    Model(words, nTag,
      (Double.NaN +: randomLogProbVector(nTag)) +: randomProbLogMatrix(nTag, nTag + 1),
      mutable.IndexedSeq.fill(words.size)(Double.NaN) +: randomProbLogMatrix(nTag, words.size))

  /**
   * Construct a lattice and compute the best part-of-speech tagging using Viterbi decoding
   * @return The lattice and the best path
   */
  def computePosTags(example: IndexedSeq[Int], model: Model) = {
    val (_, nTag, t, b) = model.params
    def newLattice[T](v: T) = IndexedSeq.fill(example.size)(
      mutable.IndexedSeq.fill(nTag + 1)(v)
    )
    val p = newLattice(Double.NaN)
    val track = newLattice(0)

    for (i <- 0 until example.size; tag <- model.tags) {
      if (i == 0) {
        p(i)(tag) = t(0)(tag) + b(tag)(example(i))
      }
      else {
        val (prevP, prevTag) = model.tags.map(prev_tag =>
          p(i - 1)(prev_tag) + t(prev_tag)(tag)
        ).zip(model.tags).maxBy(_._1)

        p(i)(tag) = prevP + b(tag)(example(i))
        track(i)(tag) = prevTag
      }
    }

    val path = {
      val (_, lastTag) = p(example.size - 1).zipWithIndex.drop(1).maxBy(_._1)

      def buildPath(i: Int, tag: Int, accu: List[Int] = Nil): IndexedSeq[Int] =
        if (i == 0) (tag :: accu).toIndexedSeq
        else buildPath(i - 1, track(i)(tag), tag :: accu)

      buildPath(example.size - 1, lastTag)
    }

    (p.map(_.toIndexedSeq), path)
  }

  /**
   * Construct a lattice and compute the best part-of-speech tagging using Viterbi decoding
   * @return The lattice and the best path
   */
  def computePosTags[X: ClassManifest](example: IndexedSeq[String],
                                       model: Model): (IndexedSeq[IndexedSeq[Double]], IndexedSeq[Int]) =
    computePosTags(example.map(model.wordToIndex), model)

  /**
   * Construct a lattice and compute the α values (forward)
   * @return α and the log probability of the example
   */
  def computeΑ(example: IndexedSeq[Int], model: Model) = {
    val (_, nTag, t, b) = model.params
    val α = IndexedSeq.fill(example.size)(
      mutable.IndexedSeq.fill(nTag + 1)(Double.NaN)
    )

    for (i <- 0 until example.size; tag <- model.tags) {
      α(i)(tag) =
        if (i == 0)
          t(0)(tag) + b(tag)(example(i))
        else
          model.tags.map(prev_tag =>
            α(i - 1)(prev_tag) + t(prev_tag)(tag)
          ).reduce(logPlus) + b(tag)(example(i))
    }

    val α_end = (model.tags).map(tag => α(example.size - 1)(tag) + t(tag)(0)).reduce(logPlus)

    (α.map(_.toIndexedSeq), α_end)
  }

  /**
   * Construct a lattice and compute the β values (backward)
   * @return β and the log probability of the example
   */
  def computeΒ(example: IndexedSeq[Int], model: Model) = {
    val (_, nTag, t, b) = model.params
    val β = IndexedSeq.fill(example.size)(
      mutable.IndexedSeq.fill(nTag + 1)(Double.NaN)
    )

    for (i <- (example.size - 1) to 0 by -1; tag <- model.tags) {
      β(i)(tag) =
        if (i == example.size - 1)
          t(tag)(0)
        else model.tags.map(next_tag =>
          t(tag)(next_tag) + b(next_tag)(example(i + 1)) + β(i + 1)(next_tag)
        ).reduce(logPlus)
    }

    val β_start = (model.tags).map(tag => t(0)(tag) + b(tag)(example(0)) + β(0)(tag)).reduce(logPlus)

    (β.map(_.toIndexedSeq), β_start)
  }
}

/**
 * The recursive helper function that computes the model for each random restart
 */
@annotation.tailrec
def computeForwardBackwardImpl(indexedCorpus: Seq[IndexedSeq[Int]],
                               model: Model, nIter: Int,
                               prevLogJointProb: Double,
                               isConverged: (Int, Double, Double) => Boolean): (Model, Double) = {
  import Model._
  val (words, nTag, t, b) = model.params
  val ex_α_β_logProb = indexedCorpus.map(ex => {
    val (α, α_end) = computeΑ(ex, model)
    val (β, β_start) = computeΒ(ex, model)
    assert(α_end - β_start < 1e10)

    (ex, α, β, α_end)
  })
  val logJointProb = ex_α_β_logProb.map(_._4).reduce(_ + _)

  if (nIter != 0 && isConverged(nIter, prevLogJointProb, logJointProb))
    (model, logJointProb)
  else {
    val new_model = {
      // Container for collecting partial counts for t
      val c_t = IndexedSeq.fill(nTag + 1)(mutable.IndexedSeq.fill(nTag + 1)(0.0))
      // Container for collecting partial counts for b
      val c_b =
        mutable.IndexedSeq.fill(words.size)(Double.NaN) +:
          IndexedSeq.fill(nTag)(mutable.IndexedSeq.fill(words.size)(0.0))

      ex_α_β_logProb.foreach({
        case (ex, α, β, logProb) =>
          // For each example, collect partial counts for t
          for (tag1 <- model.tags) {
            c_t(0)(tag1) =
              logPlus(c_t(0)(tag1),
                t(0)(tag1) + b(tag1)(ex(0)) + β(0)(tag1) - logProb)
            c_t(tag1)(0) =
              logPlus(c_t(tag1)(0),
                α(ex.size - 1)(tag1) + t(tag1)(0) - logProb)
            for (i <- 1 to ex.size - 2; tag2 <- model.tags) {
              c_t(tag1)(tag2) =
                logPlus(c_t(tag1)(tag2),
                  α(i)(tag1) + t(tag1)(tag2) + b(tag2)(ex(i + 1)) + β(i + 1)(tag2) - logProb)
            }
          }
          // For each example, collect partial counts for b
          for (tag <- model.tags; i <- 0 until ex.size) {
            c_b(tag)(ex(i)) =
              logPlus(c_b(tag)(ex(i)),
                α(i)(tag) + β(i)(tag) - logProb)
          }
      })

      // Compute the revised t and b by normalizing the partial counts
      val new_t = (0.0 +: normalizeLog(c_t.head.drop(1))) +: c_t.tail.map(normalizeLog)
      val new_b = mutable.IndexedSeq.fill(words.size)(Double.NaN) +: c_b.tail.map(normalizeLog)

      Model(words, nTag, new_t, new_b)
    }

    computeForwardBackwardImpl(indexedCorpus, new_model, nIter + 1, logJointProb, isConverged)
  }
}

/**
 * Compute parameters for unsupervised POS tagging
 * @param corpus A collection of training examples
 * @param nTag Number of tags
 * @param isConverged A function for testing convergence.
 *                    The three parameters are: the number of current iteration,
 *                    log training probability from last iteration and
 *                    log training probability of current iteration
 * @param nRandomRestart Number of random restarts
 * @param onEachRestartCompleted The function is called asynchronously after each random restart.
 *                               The three parameters are: current round of random restart,
 *                               the resulting model and the log training probability
 * @return The trained model and the log probability it assigns to corpus
 */
def computeForwardBackward(corpus: Seq[IndexedSeq[String]],
                           nTag: Int,
                           isConverged: (Int, Double, Double) => Boolean = (i, l0, l1) => l1 < l0 && (l1 / l0) > 0.99999,
                           nRandomRestart: Int = 10,
                           onEachRestartCompleted: (Int, Model, Double) => Unit = null) = {
  import scala.actors.Futures._

  val words = corpus.flatten.distinct.toIndexedSeq
  val wordToIndex = words.zipWithIndex.toMap
  val indexedCorpus = corpus.map(_.map(wordToIndex).toIndexedSeq)

  val (bestModel, bestLogJointProb) = (1 to nRandomRestart).map(currRound => {
    val initModel = Model.newRandomModel(words, nTag)
    val (model, logJointProb) =
      computeForwardBackwardImpl(
        indexedCorpus, initModel, 0, Double.NegativeInfinity, isConverged)

    if (onEachRestartCompleted != null) {
      future {
        onEachRestartCompleted(currRound, model, logJointProb)
      }
    }
    (model, logJointProb)
  }).maxBy(_._2)

  (bestModel, bestLogJointProb)
}

//
// Using the functions defined above:
//

def onEachRestartCompleted(currRound: Int, model: Model, logJointProb: Double) {
  Console.err.println("Round " + currRound + " completed. Corpus probability = 2^" + logJointProb)
  Console.err.println()
}

def isConverged(currIter: Int, prevLogJointProb: Double, currLogJointProb: Double) = {
  Console.err.println("Iteration " + currIter + " completed. Corpus probability = 2^" + currLogJointProb)
  val r = currLogJointProb / prevLogJointProb
  if (r > 0.99999 && r <= 1) {
    Console.err.println("Converged.")
    true
  }
  else false
}

val nTag = 2
val corpus =
  io.Source.fromFile( """./TRAIN1""").getLines().
    map(_.trim).filterNot(_.isEmpty).
    map(_.split(' ').toIndexedSeq).toIndexedSeq

val (model, prob) = computeForwardBackward(corpus, nTag, isConverged, 10, onEachRestartCompleted)
println("Best corpus probability = 2^" + prob)
model.b.zipWithIndex.drop(1).foreach({
  case (words, i) => {
    println("Tag " + i + ":")
    words.zipWithIndex.
      map(t => (t._1, model.words(t._2))).
      sortBy(- _._1).
      foreach({
      case (logProb, word) =>
        println(word + ":\t" + math.pow(2, logProb))
    })
    println()
  }
})

println(corpus.head.take(50).mkString)
println(Model.computePosTags(corpus.head.take(50), model)._2.mkString)
#!/usr/bin/env scalas
!#

/***
  libraryDependencies  ++= Seq(
    "org.apache.commons" % "commons-lang3" % "3.1",
    "com.github.scopt" %% "scopt" % "2.1.0"
  )

  resolvers ++= Seq(
    "Sonatype Public" at "https://oss.sonatype.org/content/groups/public"
  )
*/

import collection.mutable
import java.util.Date
import java.util.logging.{ConsoleHandler, LogRecord, Formatter, Logger}
import org.apache.commons.lang3.StringUtils

val defaultMaxNIter = 50
val defaultPseudoCntFactor = 0.0
val defaultConvergeRatio = 0.9999

case class Config(fPath: String = null,
                  ePath: String = null,
                  convergeRatio: Double = defaultConvergeRatio,
                  maxNIter: Int = defaultMaxNIter,
                  pseudoCntFactor: Double = defaultPseudoCntFactor,
                  outPath: Option[String] = None)

val optionParser = new scopt.immutable.OptionParser[Config]("ibm2-align.scala") {
  def options = Seq(
    arg("<e-path>", "path to text file of target language") {
      (s, c) => c.copy(ePath = s)
    },
    arg("<f-path>", "path to text file of source language") {
      (s, c) => c.copy(fPath = s)
    },
    doubleOpt("r", "r-cvg", "convergence ratio of log probability, between 0 and 1. default " + defaultConvergeRatio) {
      (d, c) => c.copy(convergeRatio = d)
    },
    intOpt("max-iter", "max number of training iteration, default " + defaultMaxNIter) {
      (i, c) => c.copy(maxNIter = i)
    },
    doubleOpt("pseudo-cnt", "extra pseudo-count collected per shared character of a word pair " +
      "(useful for classical-modern chinese bitext, but may cause divergence), default " + defaultPseudoCntFactor) {
      (d, c) => c.copy(pseudoCntFactor = d)
    },
    opt("o", "out", "path to output file") {
      (s, c) => c.copy(outPath = Some(s))
    }
  )
}

val nl = sys.props("line.separator")

val logger = Logger.getLogger("ibm2-align")
logger.setUseParentHandlers(false)
val handler = new ConsoleHandler()
handler.setFormatter(new Formatter {
  def format(record: LogRecord): String = {
    "[%s] %s: %s%s".format(new Date().toString, record.getLevel, record.getMessage, nl)
  }
})
logger.addHandler(handler)

val config = optionParser.parse(args, Config()).getOrElse {
  sys.exit(1)
}

def fileToSentences(path: String): Iterator[IndexedSeq[String]] =
  io.Source.fromFile(path, "UTF-8").getLines().map(l => StringUtils.split(l).toIndexedSeq)

logger.info("Putting corpus in memory...")

// (Un)fortunately, our corpus is small enough to be put in memory
val sentPairs = (fileToSentences(config.ePath) zip fileToSentences(config.fPath)).toIndexedSeq

//val sentPairs = List(
//  (IndexedSeq("this", "is", "a", "small", "house"), IndexedSeq("这", "是", "一个", "小", "房子")),
//  (IndexedSeq("that", "is", "a", "small", "house"), IndexedSeq("那", "是", "一个", "小", "房子")),
//  (IndexedSeq("this", "is", "a", "house"), IndexedSeq("这", "是", "一个", "房子")),
//  (IndexedSeq("that", "is", "a", "house"), IndexedSeq("那", "是", "一个", "房子")),
//  (IndexedSeq("this", "is", "a", "big", "apple"), IndexedSeq("这", "是", "一个", "大", "苹果")),
//  (IndexedSeq("that", "is", "a", "big", "apple"), IndexedSeq("那", "是", "一个", "大", "苹果")),
//  (IndexedSeq("this", "is", "an", "apple"), IndexedSeq("这", "是", "一个", "苹果")),
//  (IndexedSeq("that", "is", "an", "apple"), IndexedSeq("那", "是", "一个", "苹果"))
//)

logger.info("nSent = %d".format(sentPairs.size))

logger.info("Collecting vocabulary...")

val eVocab = sentPairs.flatMap(_._1).toSet
val fVocab = sentPairs.flatMap(_._2).toSet

def vocab2Maps(vocab: Set[String]): (IndexedSeq[String], Map[String, Int]) = {
  // index starts with 1, reserve 0 for NULL
  val indexToWord = Array.ofDim[String](vocab.size + 1)
  val wordToIndex = mutable.Map[String, Int]()

  vocab.foreach(w => {
    if (!wordToIndex.contains(w)) {
      val i = wordToIndex.size + 1
      indexToWord(i) = w
      wordToIndex(w) = i
    }
  })

  (indexToWord.toIndexedSeq, wordToIndex.toMap)
}

val (eIdxToWord, eWordToIdx) = vocab2Maps(eVocab)
val (fIdxToWord, fWordToIdx) = vocab2Maps(fVocab)

logger.info("eVocab.size = %d, fVocab.size = %d".format(eVocab.size, fVocab.size))

val pseudoCnt = mutable.Map[(Int, Int), Int]()
val eIdxRange = 1 to eVocab.size
val fIdxRange = 0 to fVocab.size

logger.info("Indexing sentence pairs...")

val idxSentPairs = sentPairs.map({
  case (eSent, fSent) =>
    (eSent.map(eWordToIdx), 0 +: fSent.map(fWordToIdx))
})

val maxLE = idxSentPairs.map(_._1.length).max
val maxLF = idxSentPairs.map(_._2.length).max

logger.info("max(l_e) = %d, max(l_f) = %d".format(maxLE, maxLF))


logger.info("Initializing parameters...")

// t[f:e] = t(e|f), initialized to uniform distribution
val `t[f:e]` = mutable.Map[(Int, Int), Double]().withDefaultValue(1.0 / eVocab.size)

// a[l_e,l_f,i_e:i_f] = a(i_f|i_e,l_f,l_e)
val `a[l_e,l_f,i_e:i_f]` = mutable.IndexedSeq.tabulate(maxLE + 1, maxLF + 1)(
  (l_e, l_f) => mutable.IndexedSeq.tabulate(l_e, l_f)(
    (i_e, i_f) => 1.0 / l_f
  )
)

@scala.annotation.tailrec
def LearnParams(prevLogProb: Double = Double.NegativeInfinity, nIter: Int = 1, trainModel2: Boolean = false) {
  // E-step
  logger.info("Iteration %d, E-step...".format(nIter))
  val `cnt[f:e]` = mutable.Map[(Int, Int), Double]().withDefaultValue(0.0)
  val `cnt[l_e,l_f,i_e:i_f]` =
    if (trainModel2)
      mutable.IndexedSeq.tabulate(maxLE + 1, maxLF + 1)(
        (l_e, l_f) => mutable.IndexedSeq.fill(l_e, l_f)(0.0)
      )
    else null

  for (((eSent, fSent), nSent) <- idxSentPairs.zipWithIndex) {
    if ((nSent + 1) % 10000 == 0 || nSent + 1 == idxSentPairs.size) {
      logger.info("\tSentence %d/%d".format(nSent + 1, idxSentPairs.size))
    }

    val `norm[i_e]` = {
      val nrm = mutable.IndexedSeq.fill(eSent.length)(0.0)

      for (i_e <- 0 until eSent.length;
           i_f <- 0 until fSent.length) {
        val e = eSent(i_e)
        val f = fSent(i_f)

        val t = `t[f:e]`(f, e)
        val a = `a[l_e,l_f,i_e:i_f]`(eSent.length)(fSent.length)(i_e)(i_f)

        nrm(i_e) += t * a
      }

      nrm
    }

    for (i_e <- 0 until eSent.length;
         i_f <- 0 until fSent.length) {
      val f = fSent(i_f)
      val e = eSent(i_e)

      val t = `t[f:e]`(f, e)
      val a = `a[l_e,l_f,i_e:i_f]`(eSent.length)(fSent.length)(i_e)(i_f)
      val n = `norm[i_e]`(i_e)

      val p = config.pseudoCntFactor * pseudoCnt.getOrElseUpdate(f -> e,
          if (f == 0) 0 else (eIdxToWord(e) intersect fIdxToWord(f)).length)

      val cnt = t * a / n + p

      `cnt[f:e]`(f, e) += cnt
      if (trainModel2) {
        `cnt[l_e,l_f,i_e:i_f]`(eSent.length)(fSent.length)(i_e)(i_f) += cnt
      }
    }
  }

  // M-step
  logger.info("Iteration %d, M-Step...".format(nIter))
  val norm_f = mutable.Map[Int, Double]().withDefaultValue(0.0)
  `cnt[f:e]`.foreach({
    case ((f, _), c) =>
      norm_f(f) += c
  })
  `cnt[f:e]`.foreach({
    case ((f, e), c) =>
      `t[f:e]`((f, e)) = c / norm_f(f)
  })

  if (trainModel2) {
    for (l_e <- 1 to maxLE;
         l_f <- 1 to maxLF;
         i_e <- 0 until l_e) {
      val norm = `cnt[l_e,l_f,i_e:i_f]`(l_e)(l_f)(i_e).sum

      `a[l_e,l_f,i_e:i_f]`(l_e)(l_f)(i_e) = mutable.IndexedSeq.tabulate(l_f)(
        i_f => `cnt[l_e,l_f,i_e:i_f]`(l_e)(l_f)(i_e)(i_f) / norm
      )
    }
  }

  // Check convergence
  val logProb = idxSentPairs.map({
    case (eSent, fSent) =>
      (0 until eSent.length).map(i_e => {
        val e = eSent(i_e)
        math.log(
          (0 until fSent.length).map(i_f => {
            val f = fSent(i_f)
            val t = `t[f:e]`(f, e)
            val a = `a[l_e,l_f,i_e:i_f]`(eSent.length)(fSent.length)(i_e)(i_f)

            t * a
          }).sum
        )
      }).sum - eSent.length * math.log(fSent.length)
  }).sum

  val delta = logProb - prevLogProb

  logger.info("Iteration %d, log(p) = %f (Δ = %e)".format(nIter, logProb, delta))
  //  assert(delta >= 0.0)
  val isConverged = delta >= 0 && delta / logProb.abs < 1 - config.convergeRatio
  val hasMaxNIter = nIter >= config.maxNIter
  if (!isConverged && !hasMaxNIter) {
    LearnParams(logProb, nIter + 1, trainModel2)
  } else {
    if (isConverged) {
      logger.info("Converged.")
    } else if (hasMaxNIter) {
      logger.info("Reached maximun iteration number. Δlog(p)=" + delta)
    }

    if (!trainModel2) {
      logger.info("Start training Model 2...")
      LearnParams(Double.NegativeInfinity, 1, trainModel2 = true)
    }
  }
}

logger.info("Start training...")
LearnParams()

//for (f <- fIdxRange; e <- eIdxRange) {
//  val fw = fIdxToWord(f)
//  val ew = eIdxToWord(e)
//  val prob: Double = `t[f:e]`(f, e)
//  if (prob > 1e-10)
//    println("%s\t%s\t%f".format(fw, ew, prob))
//}

def findBestAlign(eSent: IndexedSeq[Int],
                  fSent: IndexedSeq[Int]): (IndexedSeq[collection.BitSet], Double) = {
  val alignResult = IndexedSeq.fill(fSent.length)(mutable.BitSet.empty)
  var totalScore = 1.0

  for (i_e <- 0 until eSent.length) {
    val e = eSent(i_e)
    val (bestIf, bestScore) = util.Random.shuffle(// Randomly break ties
      (0 until fSent.length).map(
        i_f => {
          val f = fSent(i_f)
          i_f -> `t[f:e]`(f, e) * `a[l_e,l_f,i_e:i_f]`(eSent.length)(fSent.length)(i_e)(i_f)
        })
    ).maxBy(_._2)

    alignResult(bestIf) += i_e
    totalScore *= bestScore
  }

  (alignResult, totalScore)
}

def alignToStr(nPair: Int,
               eSent: IndexedSeq[Int],
               fSent: IndexedSeq[Int],
               alignResult: IndexedSeq[collection.BitSet],
               score: Double): String = {
  val result = new mutable.StringBuilder()
  result.append("# Sentence pair (%d) source length %d target length %d alignment score : %e".
    format(nPair + 1, fSent.length - 1, eSent.length, score)).append(nl)
  result.append(eSent.map(eIdxToWord).mkString(" ")).append(nl)

  assert(fSent.length == alignResult.length)
  val aStr = (fSent.map(fIdxToWord) zip alignResult.map(_.map(_ + 1).mkString(" "))).map({
    case (w, a) =>
      "%s ({ %s })".format(if(w!=null) w else "NULL", a)
  }).mkString(" ")
  result.append(aStr)
  result.toString()
}

val outLines = idxSentPairs.iterator.zipWithIndex.map({
  case ((eSent, fSent), i) =>
    val (alignResult, score) = findBestAlign(eSent, fSent)
    alignToStr(i, eSent, fSent, alignResult, score)
})

config.outPath match {
  case Some(outPath) =>
    val writer = new java.io.PrintWriter(outPath, "UTF-8")
    outLines.foreach(writer.println _)
    writer.close()
  case None =>
    outLines.foreach(println)
}


#!/usr/bin/env scalas
!#

/***
  libraryDependencies  ++= Seq(
    "org.apache.commons" % "commons-lang3" % "3.1",
    "com.github.scopt" %% "scopt" % "2.1.0"
  )

  resolvers += "sonatype-public" at "https://oss.sonatype.org/content/groups/public"
*/


//
// This script performs simple unsupervised Chinese vocabulary extraction
// based on n-gram mutual information and contextual entropy.
// A good introduction of the algorithm is available here: http://www.matrix67.com/blog/archives/5044
//
// To run this script you need sbt script runner
// Details: http://www.scala-sbt.org/release/docs/Detailed-Topics/Scripts
//

import collection.mutable
import java.util.logging.Logger
import org.apache.commons.lang3.StringUtils

val defaultMinCount = 5
val defaultMaxWordLength = 3
val defaultMinMutualInfo = 5.0
val defaultMinContextualEntropy = 1.0


//
// CLI parameter parsing
//

case class Config(textPath: String = null,
                  maxWordLength: Int = defaultMaxWordLength,
                  minCount: Int = defaultMinCount,
                  minMutualInfo: Double = defaultMinMutualInfo,
                  minContextualEntropy: Double = defaultMinContextualEntropy,
                  favorShort: Boolean = false,
                  outPath: Option[String] = None,
                  toIncludeRegex: String = "",
                  toExcludeRegex: String = "")

val optionParser = new scopt.immutable.OptionParser[Config]("learn-vocabulary.scala") {
  def options = Seq(
    arg("<text-path>", "the path of source text file, encoded in UTF-8") {
      (s, c) => c.copy(textPath = s)
    },
    intOpt("l", "max-len", "for a sequence to be considered a word, its maximum length " +
      "(default " + defaultMaxWordLength + ")") {
      (v, c) => c.copy(maxWordLength = v)
    },
    intOpt("c", "min-count", "for a sequence to be considered a word, its least occurence number " +
      "(default " + defaultMinCount + ")") {
      (v, c) => c.copy(minCount = v)
    },
    doubleOpt("i", "min-mutual-info", "for a sequence to be considered a word, its minimum mutual information " +
      "(default " + defaultMinMutualInfo + ")") {
      (v, c) => c.copy(minMutualInfo = v)
    },
    doubleOpt("e", "min-centropy", "for a sequence to be considered a word, its minimum contextual entropy " +
      "(default " + defaultMinContextualEntropy + ")") {
      (v, c) => c.copy(minContextualEntropy = v)
    },
    flag("s", "short", "discard an n-gram if one of its subsequence has higher contextual entropy") {
      c => c.copy(favorShort = true)
    },
    opt("o", "out", "optional output path") {
      (s, c) => c.copy(outPath = Some(s))
    },
    opt("include", "a regular expression that matches n-grams to be preserved") {
      (s, c) => c.copy(toIncludeRegex = s)
    },
    opt("exclude", "a regular expression that matches n-grams to be excluded") {
      (s, c) => c.copy(toExcludeRegex = s)
    }
  )
}

val config = optionParser.parse(args, Config()).getOrElse {
  sys.exit(1)
}

val logger = Logger.getLogger("learn-vocabulary")


//
// Statistics collection
//

logger.info("Counting n-grams...")

val nGramTotalCount = mutable.Map[Int, Int]().withDefaultValue(0)

// Should use TrieMap if we had scala 2.10...
val nGramCount = mutable.Map[String, Int]().withDefaultValue(0)
val nGramPrefix = new mutable.HashMap[String, mutable.Set[String]] with mutable.MultiMap[String, String]
val nGramSuffix = new mutable.HashMap[String, mutable.Set[String]] with mutable.MultiMap[String, String]

val subsents = io.Source.fromFile(config.textPath, "UTF-8").getLines().
  flatMap(StringUtils.split(_, "，。；：“”‘’《》！？、·（）「」『』…\r\n!@#$%^&*()_-=+][{}\\|;:'\",./<>?"))
subsents.map("/" + _ + "/").foreach(s => {
  for (n <- 1 to config.maxWordLength + 1) {
    for (i <- 0 to s.length - n) {
      val nGram = s.substring(i, i + n)
      nGramCount(nGram) += 1
      nGramTotalCount(n) += 1

      if (i - 1 >= 0)
        nGramPrefix.addBinding(nGram, s(i - 1).toString)

      if (i + n < s.length)
        nGramSuffix.addBinding(nGram, s(i + n).toString)
    }
  }
})

val nGrams =
  nGramCount.
    filter(_._2 >= config.minCount).keySet.
    filter(s =>
    s.matches(config.toIncludeRegex) ||
      s.length > 1 && s.length <= config.maxWordLength &&
        !s.startsWith("/") && !s.endsWith("/"))

//
// Core
//

val log2 = {
  val lnOf2 = scala.math.log(2)
  (d: Double) => scala.math.log(d) / lnOf2
}

logger.info("Computing mutual information...")

val nGramMutualInfo = nGrams.iterator.map({
  s =>
    val p = 1.0 * nGramCount(s) / nGramTotalCount(s.length)
    val minfo = (1 to s.length - 1).map(i => {
      val t1 = s.substring(0, i)
      val p1 = 1.0 * nGramCount(t1) / nGramTotalCount(t1.length)
      val t2 = s.substring(i)
      val p2 = 1.0 * nGramCount(t2) / nGramTotalCount(t2.length)

      log2(p / p1 / p2)
    }).min

    s -> minfo
}).filter({
  case (w, mi) =>
    w.matches(config.toIncludeRegex) || mi >= config.minMutualInfo
}).toMap


logger.info("Computing contextual entropy...")

val nGramContextualEntropy = {
  val centropy = nGramMutualInfo.keys.iterator.map({
    word =>
      val prefixEntropy = {
        val pfWords = nGramPrefix.get(word).getOrElse(Set.empty[String]).map(_ + word)
        val norm = pfWords.iterator.map(nGramCount).sum
        pfWords.iterator.map(pfw =>
          if (pfw.startsWith("/")) {
            // count each "/" as a different prefix
            val p = 1.0 / norm
            -p * log2(p) * nGramCount(pfw)
          } else {
            val p = 1.0 * nGramCount(pfw) / norm
            -p * log2(p)
          }).sum
      }

      val suffixEntropy = {
        val wordSfs = nGramSuffix.get(word).getOrElse(Set.empty[String]).map(word + _)
        val norm = wordSfs.iterator.map(nGramCount).sum
        wordSfs.iterator.map(wsf =>
          if (wsf.endsWith("/")) {
            // count each "/" as a different suffix
            val p = 1.0 / norm
            -p * log2(p) * nGramCount(wsf)
          } else {
            val p = 1.0 * nGramCount(wsf) / norm
            -p * log2(p)
          }).sum
      }

      word -> math.min(prefixEntropy, suffixEntropy)
  }).filter({
    case (w, e) =>
      w.matches(config.toIncludeRegex) || e >= config.minContextualEntropy
  }).toMap

  if (config.favorShort)
    centropy.filter({
      case (w, e) =>
        def toNGrams(n: Int) = (0 to w.length - n).map(i => w.substring(i, i + n))
        w.matches(config.toIncludeRegex) ||
          (2 to w.length - 1).iterator.flatMap(toNGrams).
            forall(sebSeq => centropy.getOrElse(sebSeq, 0.0) < e)
    })
  else
    centropy
}


//
// Output
//

logger.info("Writing output...")

val outLines = nGramContextualEntropy.keys.toSeq.
  filter(s => s.matches(config.toIncludeRegex) || !s.matches(config.toExcludeRegex)).
  map(s => (s, nGramCount(s), nGramMutualInfo(s), nGramContextualEntropy(s))).
  sortBy(t => (t._2, t._3, t._4)).iterator.map({
  case (s, cnt, mi, ce) => s + "\t" + cnt + "\t" + mi + "\t" + ce
})

config.outPath match {
  case Some(outPath) =>
    val writer = new java.io.PrintWriter(outPath, "UTF-8")
    outLines.foreach(writer.println _)
    writer.close()
  case None =>
    outLines.foreach(println)
}

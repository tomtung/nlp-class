#!/usr/bin/env scalas
!#

/***
  libraryDependencies ++= Seq(
    "org.apache.commons" % "commons-lang3" % "3.1",
    "com.github.scopt" %% "scopt" % "2.1.0"
  )

  resolvers += "sonatype-public" at "https://oss.sonatype.org/content/groups/public"
*/

// This script learns a dictionary from classical & modern Chinese bi-text by looking for common n-grams (n=2 & 3)

case class Config(text1Path: String = null,
                  text2Path: String = null,
                  vocabInPath: Option[String] = None,
                  outPath: Option[String] = None)

val optionParser = new scopt.immutable.OptionParser[Config]("learn-bitext-vocabulary.scala") {
  def options = Seq(
    arg("<text1-path>", "path of the first text file") {
      (s, c) => c.copy(text1Path = s)
    },
    arg("<text2-path>", "path of the second text file") {
      (s, c) => c.copy(text2Path = s)
    },
    opt("vocab-in", "comma separated paths, words should appear in all of them (the first columns are considered the word)") {
      (s, c) => c.copy(vocabInPath = Some(s))
    },
    opt("out", "path of resulting dictionary output file, one word per-line") {
      (s, c) => c.copy(outPath = Some(s))
    }
  )
}

val config = optionParser.parse(args, Config()).getOrElse {
  sys.exit(1)
}


val vocabIn = config.vocabInPath match {
  case Some(paths) => Some(
    paths.split(',').map(path =>
      io.Source.fromFile(path).getLines().
        map(_.trim.split("\\s+")).
        filter(!_.isEmpty).
        map(_(0)).toSet
    ).reduce(_ intersect _)
  )
  case None => None
}

//vocabIn.get.foreach(println)
//sys.exit()

//println(vocabIn.get.size)

def lineToNGrams(line: String, n: Int): List[String] = {
  import org.apache.commons.lang3.StringUtils
  val tokens = StringUtils.split(line, "，。；：“”‘’《》！？、·「」『』…")

  // N-gram model on character basis
  def tokensToNGrams(token: String) = {
    if (token.length < n) List.empty[String]
    else {
      (0 to token.length - n).map(i => token.substring(i, i + n)).toList
    }
  }

  tokens.toList.flatMap(tokensToNGrams _)
}

val vocabulary: collection.Set[String] = {
  val vocabulary = collection.mutable.Set[String]()

  val linePairs =
    io.Source.fromFile(config.text1Path, "UTF-8").getLines() zip
      io.Source.fromFile(config.text2Path, "UTF-8").getLines()

  linePairs.zipWithIndex.foreach({
    case ((line1, line2), i) =>
      val trigrams =
        lineToNGrams(line1, 3).toSet intersect
          lineToNGrams(line2, 3).toSet
      val bigrams =
        lineToNGrams(line1, 2).toSet intersect
          lineToNGrams(line2, 2).toSet

      val newWords = trigrams ++ bigrams

      vocabulary ++= (vocabIn match {
        case Some(isIn) => newWords.filter(isIn.contains _)
        case None => newWords
      })
  })

  vocabulary
}

config.outPath match{
  case Some(outPath) =>
    val writer = new java.io.PrintWriter(outPath, "UTF-8")
    vocabulary.foreach(writer.println _)
    writer.close()
  case None =>
    vocabulary.foreach(println)
}

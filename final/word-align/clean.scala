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

val defaultMaxNToken = 25

val defaultMaxNTokenRatio = 3.0

case class Config(corpusName: String = null,
                  language1Name: String = null,
                  language2Name: String = null,
                  cleanCorpusName: String = null,
                  maxNToken: Int = defaultMaxNToken,
                  maxNTokenRatio: Double = defaultMaxNTokenRatio,
                  excludeRegex: String = "")

val optionParser = new scopt.immutable.OptionParser[Config]("clean.scala") {
  def options = Seq(
    arg("<corpus>", "base corpus file name") {
      (s, c) => c.copy(corpusName = s)
    },
    arg("<language1>", "file name suffix corresponds to language1") {
      (s, c) => c.copy(language1Name = s)
    },
    arg("<language2>", "file name suffix corresponds to language2") {
      (s, c) => c.copy(language2Name = s)
    },
    arg("<clean-corpus>", "base clean output corpus file name") {
      (s, c) => c.copy(cleanCorpusName = s)
    },
    intOpt("max-n", "max number of tokens in each line, defaul " + defaultMaxNToken){
      (i, c) => c.copy(maxNToken = i)
    },
    doubleOpt("max-ratio", "max token number ratio of corresponding lines, default " + defaultMaxNTokenRatio){
      (d, c) => c.copy(maxNTokenRatio = d)
    },
    opt("exclude-regex", "lines matching this regex shall be excluded"){
      (s, c) => c.copy(excludeRegex = s)
    }
  )
}

val config = optionParser.parse(args, Config()).getOrElse {
  sys.exit(1)
}

val inputLines =
  io.Source.fromFile(config.corpusName + "." + config.language1Name).getLines() zip
    io.Source.fromFile(config.corpusName + "." + config.language2Name).getLines()

def isClean(linePair: (String, String)): Boolean = {
  val (l1, l2) = linePair

  val (t1, t2) = {
    import org.apache.commons.lang3.StringUtils
    (StringUtils.split(l1).toIndexedSeq, StringUtils.split(l2).toIndexedSeq)
  }

  if (t1.size > config.maxNToken || t2.size > config.maxNToken) false
  else if (1.0 * t1.size / t2.size > config.maxNTokenRatio || 1.0 * t2.size / t1.size > config.maxNTokenRatio) false
  else if (l1.matches(config.excludeRegex) || l2.matches(config.excludeRegex)) false
  else true
}

val lang1Writer = new java.io.PrintWriter(config.cleanCorpusName + "." + config.language1Name)
val lang2Writer = new java.io.PrintWriter(config.cleanCorpusName + "." + config.language2Name)

inputLines.filter(isClean).foreach({
  case (l1, l2) =>
    lang1Writer.println(l1)
    lang2Writer.println(l2)
})

lang1Writer.close()
lang2Writer.close()

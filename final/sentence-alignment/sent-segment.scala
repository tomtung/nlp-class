#!/usr/bin/env scalas
!#

/***
  resolvers += "sonatype-public" at "https://oss.sonatype.org/content/groups/public"

  libraryDependencies += "com.github.scopt" %% "scopt" % "2.1.0"
*/

import java.io.File
case class Config(inputPath: File = null,
                  outputPath: File = null,
                  subSent: Boolean = false,
                  uniPunc: Boolean = false)

val optionParser = new scopt.immutable.OptionParser[Config]("sent-segment.scala") {
  def options = Seq(
    flag("subsent", "split at commas"){
      c => c.copy(subSent = true)
    },
    flag("uni-punc", "for each line, replace all but last punctuation marks with '，'"){
      c => c.copy(uniPunc = true)
    },
    arg("<input-path>", "input file path") {
      (v, c) => {
        val f = new File(v)
        if (!f.exists() || !f.isFile) {
          System.err.println("ERROR: input file " + v + " not found.")
          sys.exit(1)
        }
        c.copy(inputPath = f)
      }
    },
    arg("<output-path>", "output file path") {
      (v, c) => c.copy(outputPath = new File(v))
    }
  )
}

val config = optionParser.parse(args, Config()).getOrElse {
  sys.exit(1)
}

def splitParagraph(paragraph: String): Array[String] = {
  val rSentEnd =
    if (config.subSent)
      "[，。；！？：](?:\\s*[”’」』])?".r
    else
      "[。；！？：](?:\\s*[”’」』])?".r

  val nl = sys.props("line.separator")
  rSentEnd.replaceAllIn(paragraph, _ + nl).split(nl).map(_.trim) :+ "<p>"
}

def uniPunc(line: String): String = {
  if (!config.uniPunc) line
  else line.replaceAll("""[。；！？：](?!$)""", "，")
}

val writer = new java.io.PrintWriter(config.outputPath, "UTF-8")

io.Source.fromFile(config.inputPath, "UTF-8").getLines().
  map(uniPunc).
  flatMap(splitParagraph _).
  foreach(writer.println _)

writer.close()

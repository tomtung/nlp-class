#!/usr/bin/env scalas
!#

/***
  resolvers += "sonatype-public" at "https://oss.sonatype.org/content/groups/public"

  libraryDependencies += "com.github.scopt" %% "scopt" % "2.1.0"
*/

import java.io.File
case class Config(inputPath: File = null, outputPath: File = null, subsent: Boolean = false)

val optionParser = new scopt.immutable.OptionParser[Config]("sent-segment.scala") {
  def options = Seq(
    flag("subsent", "split at commas"){
      c => c.copy(subsent = true)
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
    if (config.subsent)
      "[，。；！？：](?:\\s*[”’」』])?".r
    else
      "[。；！？：](?:\\s*[”’」』])?".r

  val nl = sys.props("line.separator")
  rSentEnd.replaceAllIn(paragraph, _ + nl).split(nl).map(_.trim) :+ "<p>"
}

val writer = new java.io.PrintWriter(config.outputPath, "UTF-8")

io.Source.fromFile(config.inputPath, "UTF-8").getLines().
  flatMap(splitParagraph _).
  foreach(writer.println _)

writer.close()

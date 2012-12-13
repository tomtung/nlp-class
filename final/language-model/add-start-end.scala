#!/usr/bin/env scalas
!#

/***
  libraryDependencies  ++= Seq(
    "org.apache.commons" % "commons-lang3" % "3.1",
    "com.github.scopt" %% "scopt" % "2.1.0"
  )

  resolvers += "sonatype-public" at "https://oss.sonatype.org/content/groups/public"
*/

case class Config(inputPath: String = null, outputPath: Option[String] = None)

val optionParser = new scopt.immutable.OptionParser[Config]("add-start-end.scala") {
  def options = Seq(
    arg("<input-path>", "path of input text file"){
      (v, c) => c.copy(inputPath = v)
    },
    argOpt("<output-path>", "path of output text file"){
      (v, c) => c.copy(outputPath = Some(v))
    }
  )
}

val sentEnd = ".+[。；！？：](?:\\s*[”’」』])?$".r

val config = optionParser.parse(args, Config()).getOrElse {
  sys.exit(1)
}

def transformLines(inputLines: Stream[String], isNewSent: Boolean): Stream[String] =
  inputLines match {
    case stream if stream.isEmpty =>
      Stream.empty
    case head #:: rest =>
      val prefix = if (isNewSent) "<s> " else ""
      val sentEnds: Boolean = sentEnd.pattern.matcher(head).matches()
      val suffix = if (sentEnds) " </s>" else ""
      (prefix + head + suffix) #:: transformLines(rest, sentEnds)
  }

val outLines = transformLines(
  io.Source.fromFile(config.inputPath, "UTF-8").getLines().toStream,
  isNewSent = true)

config.outputPath match {
  case None =>
    println("</s>")
    outLines.foreach(println)
  case Some(path) =>
    val writer = new java.io.PrintWriter(path, "UTF-8")
    writer.println("</s>")
    outLines.foreach(writer.println _)
    writer.close()
}

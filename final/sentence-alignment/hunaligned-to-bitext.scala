#!/usr/bin/env scalas
!#

/***
  resolvers += "sonatype-public" at "https://oss.sonatype.org/content/groups/public"

  libraryDependencies += "com.github.scopt" %% "scopt" % "2.1.0"
*/

case class Config(hunaligned: String = null, lang1Output: String = null, lang2Output: String = null)

val optionParser = new scopt.immutable.OptionParser[Config]("hunaligned-to-bitext.scala") {
  def options = Seq(
    arg("<hunaligned>", "the text output by hunaligned") {
      (v, c) => c.copy(hunaligned = v)
    },
    arg("<lang1-output>", "output path for the first language") {
      (v, c) => c.copy(lang1Output = v)
    },
    arg("<lang2-output>", "output path for the second language") {
      (v, c) => c.copy(lang2Output = v)
    }
  )
}

val config = optionParser.parse(args, Config()).getOrElse {
  sys.exit(1)
}

val hunalignedFile = new java.io.File(config.hunaligned)
if (!hunalignedFile.exists() || !hunalignedFile.isFile) {
  System.err.println("ERROR: input file not found")
  sys.exit(1)
}

val lang1Writer = new java.io.PrintWriter(config.lang1Output, "UTF-8")
val lang2Writer = new java.io.PrintWriter(config.lang2Output, "UTF-8")

def tidyLine(line: String): String = {
  line.replaceAll("\\s*~~~\\s*", " ").
    replaceAll("\\s*<p>\\s*", " ").
    replaceAll("\\s+", " ").
    trim
}

io.Source.fromFile(hunalignedFile, "UTF-8").
  getLines().zipWithIndex.foreach {
  case (line, i) =>
    val columns = line.split('\t')
    if (columns.length != 3) {
      System.err.println("ERROR: Format error at line " + (i + 1))
      sys.exit(1)
    }

    val lang1Line = tidyLine(columns(0))
    val lang2Line = tidyLine(columns(1))

    if (!lang1Line.isEmpty || !lang2Line.isEmpty){
      if (lang1Line.isEmpty || lang2Line.isEmpty){
        System.err.println("WARNING: possible alignment error at line " + (i + 1))
      }

      lang1Writer.println(lang1Line)
      lang2Writer.println(lang2Line)
    }
}

lang1Writer.close()
lang2Writer.close()

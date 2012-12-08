#!/usr/bin/env scalas
!#

/***
  resolvers += "sonatype-public" at "https://oss.sonatype.org/content/groups/public"

  libraryDependencies += "com.github.scopt" %% "scopt" % "2.1.0"
*/

// This script is similar to the partialAlign.py script provided by hunalign,
// but it makes sure that same paragraphs (denoted by <p>) always be put into same corresponding files

case class Config(maxLineNum: Int = 5000,
                  batchFileName: String = null,
                  lang1Text: String = null,
                  lang2Text: String = null,
                  outputFileName: String = null,
                  lang1Name: String = null,
                  lang2Name: String = null)

val optionParser = new scopt.immutable.OptionParser[Config]("hunalign-partial.scala") {
  def options = Seq(
    intOpt("max-line-num", "max number of lines in each file"){(v, c) => c.copy(maxLineNum = v)},
    arg("<batch-file-name>", "batch file name"){(v, c) => c.copy(batchFileName = v)},
    arg("<lang1-text>", "large text file in the first language"){(v, c) => c.copy(lang1Text = v)},
    arg("<lang2-text>", "large text file in the second language"){(v, c) => c.copy(lang2Text = v)},
    arg("<output-filename>", "output filename. Output is a set of files named output-filename_[123..].lang-name"){
      (v, c) => c.copy(outputFileName = v)},
    arg("<lang1-name>", "name of the first language"){(v, c) => c.copy(lang1Name = v)},
    arg("<lang2-name>", "name of the first language"){(v, c) => c.copy(lang2Name = v)}
  )
}

val config = optionParser.parse(args, Config()).getOrElse {
  sys.exit(1)
}

def nthOutputFileName(n: Int, langName: String) = "%s_%d.%s".format(config.outputFileName, n, langName)
def getNthWriter(n: Int, langName: String) = new java.io.PrintWriter(nthOutputFileName(n, langName), "UTF-8")
def getNthWriters(n: Int) = (getNthWriter(n, config.lang1Name), getNthWriter(n, config.lang2Name))

def linesToParagraphs(lines: Stream[String]): Stream[Seq[String]] =
  lines match {
    case Stream() =>
      Stream.empty
    case _ =>
      val paragraph = lines.takeWhile(_ != "<p>").toIndexedSeq :+ "<p>"
      val rest = lines.drop(paragraph.size)
      paragraph #:: linesToParagraphs(rest)
  }

def paragraphsOfFile(fileName: String): Stream[Seq[String]] =
  linesToParagraphs(
    io.Source.fromFile(fileName, "UTF-8").getLines().toStream)

val lang1Paragraphs = paragraphsOfFile(config.lang1Text)
val lang2Paragraphs = paragraphsOfFile(config.lang2Text)

val paragraphPairs = lang1Paragraphs.zipAll(lang2Paragraphs, Seq.empty, Seq.empty)

def writeParagraphs(batchFileWriter: java.io.PrintWriter)
                   (paragraphPairs: Stream[(Seq[String], Seq[String])],
                    writerPair: (java.io.PrintWriter, java.io.PrintWriter),
                    currLineNums: (Int, Int),
                    n: Int) {
  paragraphPairs match {
    case Stream() =>
      writerPair._1.close()
      writerPair._2.close()

      (1 to n).foreach(
        i => batchFileWriter.println(
          nthOutputFileName(i, config.lang1Name) + "\t" +
            nthOutputFileName(i, config.lang2Name) + "\t" +
            nthOutputFileName(i, "aligned")
        ))

    case (para1, para2) #:: rest =>
      val newLineNums = (currLineNums._1 + para1.length, currLineNums._2 + para2.length)
      if (newLineNums._1 > config.maxLineNum || newLineNums._2 > config.maxLineNum) {
        writerPair._1.close()
        writerPair._2.close()
        writeParagraphs(batchFileWriter)(paragraphPairs, getNthWriters(n + 1), (0, 0), n + 1)
      } else {
        para1.foreach(writerPair._1.println _)
        para2.foreach(writerPair._2.println _)
        writerPair._1.flush()
        writerPair._2.flush()
        writeParagraphs(batchFileWriter)(rest, writerPair, newLineNums, n)
      }
  }
}

val batchFileWriter = new java.io.PrintWriter(config.batchFileName, "UTF-8")
writeParagraphs(batchFileWriter)(paragraphPairs, getNthWriters(1), (0, 0), 1)
batchFileWriter.close()
#!/usr/bin/env scalas
!#

/***
libraryDependencies ++= Seq(
  "org.apache.commons" % "commons-lang3" % "3.1"
)
*/

val goldenTestLinePairs = {
  if (args.length != 2) {
    System.err.println("Usage:")
    System.err.println("\tbleu-eval.scala golden-file test-file")
    System.err.println("\tbleu-eval.scala golden-dir test-dir")
    System.err.println()
    sys.exit(1)
  }

  import java.io.File

  val goldenFile = new File(args(0))
  val testFile = new File(args(1))

  if (!goldenFile.exists()) {
    System.err.println("ERROR: Golden file " + goldenFile.getName + " not found.")
    sys.exit(1)
  }

  if (!testFile.exists()) {
    System.err.println("ERROR: Test file " + testFile.getName + " not found.")
    sys.exit(1)
  }

  if (!(goldenFile.isDirectory && testFile.isDirectory || goldenFile.isFile && testFile.isFile)) {
    System.err.println("ERROR: Input paths should be both normal files or both directories")
    sys.exit()
  }

  val goldenTestFilePairs: List[(File, File)] =
    if (goldenFile.isDirectory) {
      val goldenFiles = goldenFile.listFiles().filter(_.isFile)
      val testFiles = testFile.listFiles().filter(_.isFile)

      val diff = goldenFiles.map(_.getName) diff testFiles.map(_.getName)
      if (!diff.isEmpty) {
        System.err.println("ERROR: I can't find the corresponding test files for the following golden files:")
        diff.foreach(System.err.println _)
      }

      val nameToGoldenFile = goldenFiles.map(f => f.getName -> f).toMap
      val nameToTestFile = testFiles.map(f => f.getName -> f).toMap

      def generateFilePairs(fileNames: List[String], accu: List[(File, File)] = Nil): List[(File, File)] =
        fileNames match {
          case head :: tail =>
            generateFilePairs(tail, (nameToGoldenFile(head), nameToTestFile(head)) :: accu)
          case Nil =>
            accu
        }

      generateFilePairs(nameToGoldenFile.keys.toList)
    } else {
      List((goldenFile, testFile))
    }

  val lineNumDiffPairs: List[(File, File)] = goldenTestFilePairs.filter({
    case (gf, tf) =>
      val gs = io.Source.fromFile(gf, "UTF-8")
      val ts = io.Source.fromFile(tf, "UTF-8")
      gs.getLines().length != ts.getLines().length
  })

  if (!lineNumDiffPairs.isEmpty) {
    System.err.println("ERROR: different number of lines in golden and test files:")
    lineNumDiffPairs.iterator.map(_._1.getName).foreach(System.err.println _)
    sys.exit(1)
  }

  val goldenTestLinePairs = goldenTestFilePairs.flatMap({
    case (gf, tf) =>
      val gs = io.Source.fromFile(gf, "UTF-8")
      val ts = io.Source.fromFile(tf, "UTF-8")

      gs.getLines() zip ts.getLines()
  })

  goldenTestLinePairs
}

def lineToNGrams(line: String, n: Int) = {
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

def linePairsAverageNBleu(goldenTestLinePairs: List[(String, String)], n: Int): Double = {
  val bleus = goldenTestLinePairs.filterNot(_._1.isEmpty).map({
    case (gl, tl) =>
      val precisions = (1 to n).map(i => {
        val goldenNGramCounts = lineToNGrams(gl, i).groupBy(identity).mapValues(_.size)
        val testNGramCounts = lineToNGrams(tl, i).groupBy(identity).mapValues(_.size)
        if (goldenNGramCounts.isEmpty) 1.0
        else if (testNGramCounts.isEmpty) 0.0
        else {
          val correctNGramCounts = testNGramCounts.filterKeys(goldenNGramCounts.contains).map({
            case (t, c) => t -> math.min(c, goldenNGramCounts(t))
          })

          correctNGramCounts.values.sum / testNGramCounts.values.sum.asInstanceOf[Double]
        }
      })

      val penalty = math.min(math.exp(1 - gl.length.asInstanceOf[Double] / tl.length), 1.0)

      penalty * math.pow(precisions.reduce(_ * _), 1.0/n)
  })

  bleus.sum / bleus.size
}

def linePairsBleu(goldenTestLinePairs: List[(String, String)], n: Int): Double = {
  val precisions = (1 to n).map(i => {
    val goldenNGramCounts = goldenTestLinePairs.map(_._1).flatMap(l => lineToNGrams(l, i)).groupBy(identity).mapValues(_.size)
    val testNGramCounts = goldenTestLinePairs.map(_._2).flatMap(l => lineToNGrams(l, i)).groupBy(identity).mapValues(_.size)
    
    if (goldenNGramCounts.isEmpty) 1.0
    else if (testNGramCounts.isEmpty) 0.0
    else {
      val correctNGramCounts = testNGramCounts.filterKeys(goldenNGramCounts.contains).map({
	case (t, c) => t -> math.min(c, goldenNGramCounts(t))
      })

      correctNGramCounts.values.sum / testNGramCounts.values.sum.asInstanceOf[Double]
    }
  })
  
  val gLength = goldenTestLinePairs.map(_._1.length).sum
  val tLength = goldenTestLinePairs.map(_._2.length).sum
  val penalty = math.min(math.exp(1 - gLength.asInstanceOf[Double] / tLength), 1.0)

  penalty * math.pow(precisions.reduce(_ * _), 1.0/n)
}

for (i <- 1 to 4) {
  println(i + "-BLEU: " + linePairsBleu(goldenTestLinePairs, i))
}

for (i <- 1 to 4) {
  println("Average " + i + "-BLEU of each line: " + linePairsAverageNBleu(goldenTestLinePairs, i))
}

#!/usr/bin/env scalas
!#

/***
  unmanagedBase ~= {x => new java.io.File("../lib")}

  resolvers += "sonatype-public" at "https://oss.sonatype.org/content/groups/public"

  libraryDependencies += "com.github.scopt" %% "scopt" % "2.1.0"
*/


import java.io.File

object Mode extends Enumeration {
  val crf, mfm, naive = Value
}

case class Config(inputFile: File = null,
                  outputPath: String = null,
                  mode: Mode.Value = null,
                  vocabPath: Option[String] = None)

val optionParser = new scopt.immutable.OptionParser[Config]("tokenize.scala") {
  def options = Seq(
    opt("vocab", "comma separated list of vocabulary files") {
      (v, c) => c.copy(vocabPath = Some(v))
    },
    arg("<mode>", "tokenization mode, either " + Mode.values.mkString(" or ")) {
      (v, c) =>
        try {
          c.copy(mode = Mode.withName(v))
        } catch {
          case e: Throwable =>
            System.err.println("Unknown mode " + v)
            sys.exit(1)
        }
    },
    arg("<input-path>", "path of input file to be tokenized") {
      (v, c) => {
        val f = new File(v)
        if (!f.exists() || !f.isFile) {
          System.err.println("ERROR: input file " + v + " not found.")
          sys.exit(1)
        }
        c.copy(inputFile = f)
      }
    },
    arg("<output-path>", "path of output tokenized file") {
      (v, c) => c.copy(outputPath = v)
    }
  )
}

val config = optionParser.parse(args, Config()).getOrElse {
  sys.exit(1)
}
lazy val tokenize = config.mode match {
  case Mode.naive =>
    (s: String) => s.map(_.toString)

  case Mode.mfm =>
    // Simple Maximum Forward Match
    val vocab = config.vocabPath.getOrElse("").split(',').iterator.
      flatMap(io.Source.fromFile(_, "UTF-8").getLines()).
      toSet

    val maxLength = vocab.map(_.length).max

    (s: String) => {
      val result = collection.mutable.ListBuffer[String]()

      def tk(i: Int) {
        if (i < s.length) {
          val n =
            (maxLength to 1 by -1).
              find(n => i + n < s.length && vocab.contains(s.substring(i, i + n))).
              getOrElse(1)
          result += s.substring(i, i + n)
          tk(i + n)
        }
      }
      tk(0)

      result.toList
    }

  case Mode.crf =>
    val classifier = {
      val dataDir: String = "../lib/stanford-segmenter-1.6.7-data/"
      val props = {
        val p = new java.util.Properties()
        p.setProperty("sighanCorporaDict", dataDir)
        p.setProperty("sighanPostProcessing", "true")
        p.setProperty("useChPos", "true")
        p.setProperty("serDictionary",
          dataDir + "dict-chris6.ser.gz," +
            config.vocabPath.getOrElse(""))
        p.setProperty("inputEncoding", "UTF-8")
        p
      }

      import edu.stanford.nlp.ie.crf.CRFClassifier
      import edu.stanford.nlp.ling.CoreLabel
      val c = new CRFClassifier[CoreLabel](props)
      c.loadClassifier(dataDir + "pku.gz", props)
      c.flags.setProperties(props)
      c
    }

    import scala.collection.JavaConversions._
    (s: String) => classifier.segmentString(s).toList
}

val writer = new java.io.PrintWriter(config.outputPath, "UTF-8")

io.Source.fromFile(config.inputFile, "UTF-8").getLines().
  map(line => tokenize(line).mkString(" ")).
  foreach(writer.println _)

writer.close()

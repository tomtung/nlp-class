import sys.process._

val path = "tri.wfsa"

def lines =
  (io.Source.fromFile("./TRAIN").getLines() ++
    io.Source.fromFile("./HELDOUT").getLines()).
//  io.Source.fromFile("./TMP").getLines().
  filterNot(_.trim.isEmpty).
  map("^ " + _ + " $")

println("#Train\tlog2(P(HELDOUT))\tlog2(P(TEST))")
//for(n <- List(lines.size)){
for(n <- 40 to lines.size by 20){
  val nToken =
    lines.take(n).
      flatMap(_.split(' ').map(_.trim.toLowerCase)).
      foldLeft(Map[String,Int]().withDefaultValue(0))((m, s) => (m + (s -> (m(s) + 1))))

  def getNGramCount[T](toNGram : String => TraversableOnce[T]) =
    lines.take(n).
      flatMap(toNGram).
      foldLeft(Map[T,Int]().withDefaultValue(0))((m, p) => (m + (p -> (m(p) + 1))))

  val nBigram = {
    def toBigrams(s: String) = {
      val tokens = s.split(' ').map(_.trim.toLowerCase)
      tokens zip tokens.drop(1)
    }

    getNGramCount(toBigrams)
  }

  val nTrigram= {
    def toTrigrams(s: String) = {
      val tokens = s.split(' ').map(_.trim.toLowerCase)
      (tokens, tokens.drop(1), tokens.drop(2)).zipped
    }

    getNGramCount(toTrigrams)
  }

  def computeDefaultD[T](nNGram : Map[T, Int]) = {
    val n = nNGram.groupBy(_._2).mapValues(_.size.asInstanceOf[Double])
    val y = n(1) / (n(1) + 2*n(2))
    def yIfNeg(d: Double) =
      if (d <= 0) y
      else d

    val d1 = yIfNeg(1 - 2*y*n(2)/n(1))
    val d2 = yIfNeg(2 - 3*y*n(3)/n(2))
    val d3plus = yIfNeg(3 - 4*y*n(4)/n(2))

    (d1, d2, d3plus)
  }

  val (alpha3, gamma2) = {
    def xy2Cxy_(prefix : (String, String)) = prefix match {
      case (_, "$") => 0
      case x => nBigram(x)
    }

    val (d31, d32, d33plus) = (0.52, 0.37, 0.88) //computeDefaultD(nTrigram)
    def D3(c: Int) = c match {
      case 0 => 0.0
      case 1 => d31
      case 2 => d32
      case _ => d33plus
    }

//    println("[D31 = %.3f, D32 = %.3f, D33+ = %.3f]".format(d31, d32, d33plus))

    val alpha3 = {
      nTrigram.map({
        case ((x, y, z), c) =>
          (x, y, z) -> (c - D3(c)) / xy2Cxy_((x, y))
      })
    }

    val gamma2 = {
      val xy2c2Ncxy_ =
        nTrigram.groupBy({
          case ((x,y,z), c) => (x,y)
        }).mapValues(_.groupBy(_._2).mapValues(_.size))

      nBigram.filterKeys(_._2 != "$").map({
        case (xy, _) =>
          val sum = xy2c2Ncxy_(xy).iterator.map({
            case (c, nc) =>
              D3(c) * nc
          }).sum

          xy -> sum / xy2Cxy_(xy)
      })
    }

    (alpha3, gamma2)
  }

  val (alpha2, gamma1, p1) = {
    val yzToN1plus_yz = nTrigram.keySet.groupBy({case (x,y,z) => (y,z)}).mapValues(_.size)
    val yToN1plus_y_ = nTrigram.keySet.groupBy({case (x,y,z) => y}).mapValues(_.size)

    val (d21, d22, d23plus) = (0.55, 1.71, 2.22) // computeDefaultD(nBigram)
    def D2(c: Int) = c match {
      case 0 => 0.0
      case 1 => d21
      case 2 => d22
      case _ => d23plus
    }
//    println("[D21 = %.3f, D22 = %.3f, D23+ = %.3f]".format(d21, d22, d23plus))

    val alpha2 =
      nBigram.map({
        case (("^", x), c0x) =>
          ("^", x) -> (c0x - D2(c0x)) / nToken("^")
        case ((y, z), _) =>
          val n1plus_yz = yzToN1plus_yz((y,z))
          val n_y_ = yToN1plus_y_(y)
          (y, z) -> (n1plus_yz - D2(n1plus_yz)) / n_y_
      })

    val gamma1 = {
      val cToNc0_ = nBigram.filterKeys(_._1 == "^").groupBy(_._2).mapValues(_.size)
      val yTozToN1plus_yz =
        nTrigram.keySet.
          groupBy({case (x,y,z) => y}).
          mapValues(_.groupBy({case (x,y,z) => z}).mapValues(_.size))

      nToken.filterKeys(_ != "$").map({
        case ("^", _) =>
          val sum = cToNc0_.iterator.map({case (c, nc) => D2(c) * nc}).sum
          "^" -> sum / nToken("^")
        case (y, _) =>
          val sum = yTozToN1plus_yz(y).values.map(D2).sum
          val n_y_ = yToN1plus_y_(y)
          y -> sum / n_y_
      })
    }

    val p1 = {
      val bigrams_1PlusTimesWithSuffix =
        nBigram.groupBy(_._1._2).mapValues(_.keySet).withDefaultValue(Set.empty)

      nToken.filterKeys(_ != "^").map({
        case (x, _) =>
          x -> (0.0 + bigrams_1PlusTimesWithSuffix(x).size) / nBigram.size
      })
    }

    (alpha2, gamma1, p1)
  }

  val writer = new java.io.PrintWriter(path)
  try{
    writer.println("$")
    def transTo(s: String) =
      if (s == "$") "*e*"
      else s

    alpha2.toList.sortBy(_._1._1).map({
      case ((x, "$"), w) =>
        "(%s ($ *e* *e* %.5e))".format(x, w)
      case ((x,y), w) =>
        "(%s (%s *e* %s %.5e))".format(x, x+y, transTo(y), w)
    }).foreach(writer.println _)

    alpha3.map({
      case((x,y,"$"), w) =>
        "(%s ($ *e* *e* %.5e))".format(x+y, w)
      case((x,y,z), w) =>
        "(%s (%s *e* %s %.5e))".format(x+y, y+z, transTo(z), w)
    }).foreach(writer.println _)

    gamma2.map({
      case ((x,y), w) =>
        "(%s (%s *e* *e* %.5e))".format(x+y, y, w)
    }).foreach(writer.println _)

    gamma1.map({
      case (x, w) =>
        "(%s (? *e* *e* %.5e))".format(x, w)
    }).foreach(writer.println _)

    p1.map({
      case (x, w) =>
        "(? (%s *e* %s %.5e))".format(x, transTo(x), w)
    }).foreach(writer.println _)
  }
  finally {
    writer.close()
  }

  def getResult(dataCmd : String) = {
    val err = new StringBuilder()
    val logger = ProcessLogger(_ => {}, e => err.append(e))
    dataCmd #| ("carmel -Ssrn " + path) ! logger

    val probRegex = """probability=2\^(-(?:[\d\.]+|inf))""".r
    val perplexityRegex = """per-line-perplexity\(N=\d+\)=2\^([\-\+]?(?:[\d\.]+|inf))""".r

    def extractDouble(r: scala.util.matching.Regex) =
      r.findFirstMatchIn(err.toString()).get.group(1) match {
        case "inf" | "+inf" => Double.PositiveInfinity
        case "-inf" => Double.NegativeInfinity
        case d => d.toDouble
      }

    (extractDouble(probRegex), extractDouble(perplexityRegex))
  }

  //val (_, trainPerpLog2) = getResult("head -%d TRAIN".format(n*2))
  val (heldoutProbLog2, _) = getResult("cat HELDOUT")
  val (testProbLog2, _) = getResult("cat TEST")

  println(n + "\t" + heldoutProbLog2 + "\t" + "\t" + testProbLog2)
}

println()
println("Final size:")
("carmel -c " + path).lines_!(ProcessLogger(_ => {}, _ => {})).
  filterNot(_.trim.isEmpty).foreach(println)
println()

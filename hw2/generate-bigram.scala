import sys.process._

val path = "bi.wfsa"

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

  val nBigram ={
    def toBigrams(s: String) = {
      val tokens = s.split(' ').map(_.trim.toLowerCase)
      tokens zip tokens.drop(1)
    }

    lines.take(n).
      flatMap(toBigrams).
      foldLeft(Map[(String,String),Int]().withDefaultValue(0))((m, p) => (m + (p -> (m(p) + 1))))
  }

  def nBigramWithPrefix(prefix : String) = prefix match {
    case "$" => 0
    case p => nToken(p)
  }

  val (d1, d2, d3plus) = {
//    val n = nBigram.groupBy(_._2).mapValues(_.size.asInstanceOf[Double])
//    val y = n(1) / (n(1) + 2*n(2))
//    def yIfNeg(d: Double) =
//      if (d <= 0) y
//      else d
//
//    val d1 = yIfNeg(1 - 2*y*n(2)/n(1))
//    val d2 = yIfNeg(2 - 3*y*n(3)/n(2))
//    val d3plus = yIfNeg(3 - 4*y*n(4)/n(2))
//
//    (d1, d2, d3plus)
    (0.290, 0.048, 0.830)
  }
//  println("[D1 = %.3f, D2 = %.3f, D3+ = %.3f]".format(d1, d2, d3plus))

  def D(c: Int) = c match {
    case 0 => 0.0
    case 1 => d1
    case 2 => d2
    case _ => d3plus
  }

  val alpha2 = nBigram.map({
    case ((x,y),c) =>
      (x,y) -> (c - D(c)) / nBigramWithPrefix(x)
  })

  val gamma = {
    val bigrams_OnceWithPrefix =
      nBigram.filter(_._2 == 1).groupBy(_._1._1).mapValues(_.keySet).withDefaultValue(Set.empty)

    val bigrams_TwiceWithPrefix =
      nBigram.filter(_._2 == 2).groupBy(_._1._1).mapValues(_.keySet).withDefaultValue(Set.empty)

    val bigram_3PlusTimesWithPrefix =
      nBigram.filter(_._2 >= 3).groupBy(_._1._1).mapValues(_.keySet).withDefaultValue(Set.empty)

    nToken.filterKeys(_ != "$").map({
      case (x, _) =>
        val n1 = bigrams_OnceWithPrefix(x).size
        val n2 = bigrams_TwiceWithPrefix(x).size
        val n3plus = bigram_3PlusTimesWithPrefix(x).size
        x -> (d1*n1 + d2*n2 + d3plus*n3plus) / nBigramWithPrefix(x)
    })
  }

  val alpha1 = {
    val bigrams_1PlusTimesWithSuffix =
      nBigram.groupBy(_._1._2).mapValues(_.keys).withDefaultValue(Set.empty)

    nToken.filterKeys(_ != "^").map({
      case (x, _) =>
        x -> (0.0 + bigrams_1PlusTimesWithSuffix(x).size) / nBigram.size
    })
  }

  val writer = new java.io.PrintWriter(path)
  try{
    writer.println("$")
    def transTo(s: String) =
      if (s == "$") "*e*"
      else s

    alpha2.toList.sortBy(_._1._1).map({
      case ((x,y), w) =>
        "(%s (%s *e* %s %.5e))".format(x, y, transTo(y), w)
    }).foreach(writer.println _)

    gamma.map({
      case (x, w) =>
        "(%s (? *e* *e* %.5e))".format(x, w)
    }).foreach(writer.println _)

    alpha1.map({
      case (x, w) =>
        "(? (%s *e* %s %.5e))".format(x, transTo(x), w)
    }).foreach(writer.println _)
  }
  finally {
    writer.close()
  }

//  sys.exit()

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

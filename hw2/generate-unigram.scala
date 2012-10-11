import sys.process._

val path = "uni.wfsa"

def lines =
  (io.Source.fromFile("./TRAIN").getLines() ++
    io.Source.fromFile("./HELDOUT").getLines()).
  filterNot(_.trim.isEmpty)

println("#Train\tlog2(P(HELDOUT))\tlog2(P(TEST))")
for(n <- 40 to lines.size by 20){
  val probMap ={
    val countMap =
      lines.take(n).flatMap(s => (s + " $").split(' ')).map(_.trim.toLowerCase).
      foldLeft(Map[String, Double]().withDefaultValue(0.0))((m, s) => (m + (s -> (m(s) + 1))))

    val totalCount = countMap.values.sum

    countMap.mapValues(_ / totalCount)
  }

  val writer = new java.io.PrintWriter(path)
  try{
    writer.println("$")
    probMap.iterator.map({
      case ("$",p) =>
        "(0 ($ *e* *e* %.5e))".format(p)
      case (s, p) =>
        "(0 (0 *e* %s %.5e))".format(s, p)
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

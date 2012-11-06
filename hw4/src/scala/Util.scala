object Util {
  import math.log

  /**
   * Given l1 = log(x), l2 = log(y), compute the approximate value of log(x+y)
   */
  def logPlus(l1: Double, l2: Double) = {
    assert(!l1.isNaN && !l2.isNaN)
    val l_larger = math.max(l1, l2)
    val l_smaller = math.min(l1, l2)
    if (l_smaller.isNegInfinity) l_larger
    else if (l_larger - l_smaller > 64) l_larger
    else l_larger + log(1 + math.exp(l_smaller - l_larger))
  }
}

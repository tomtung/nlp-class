trait PosTagger {
  def computePosTag(input: IndexedSeq[String]): IndexedSeq[String]
}

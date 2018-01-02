package com.roboxue.niffler.examples.slimmer_pr

import com.roboxue.niffler.{Implementation, Niffler, Token}

/**
  * @author rxue
  * @since 1/1/18.
  */
object Application {
  def main(args: Array[String]): Unit = {
    Niffler.init(args) // making sure args is broadcasted to every niffler in this jvm
    val totalScore: Token[Int] = Token("execute all engine and calculate a total score")
    val engines = Seq(Engine1, Engine2, Engine3, Engine4)
    val accumulateScores: Seq[Implementation[Int]] = engines.map(engine => totalScore.amendWithToken(engine.scoreDoc))
    val combinedScoringEngine = Niffler.combine(engines: _*).diverge(accumulateScores)
    val averageScore = combinedScoringEngine
      .syncRun(totalScore)
      .result
      .toDouble / engines.length
    println(s"avg score is $averageScore")
    Niffler.terminate()
  }
}

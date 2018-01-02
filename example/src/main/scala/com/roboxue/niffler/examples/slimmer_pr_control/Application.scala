package com.roboxue.niffler.examples.slimmer_pr_control

/**
  * @author rxue
  * @since 1/1/18.
  */
object Application {
  def main(args: Array[String]): Unit = {
    val parsedArgs = new ArgsUtils(args)
    val file1 = parsedArgs.file1
    val file2 = parsedArgs.file2
    val stemmer = parsedArgs.stemmer
    val engine1 = new Engine1(parsedArgs.stopWordList)
    val engine2 = new Engine2()
    val engine3 = new Engine3()
    val engine4 = new Engine4()
    val engines = Seq(engine1, engine2, engine3, engine4)
    val score = engines.par
      .map(eng => {
        eng.scoreDoc(file1, file2, stemmer)
      })
      .sum
      .toDouble / engines.length
    println(s"avg score is $score")
  }

}

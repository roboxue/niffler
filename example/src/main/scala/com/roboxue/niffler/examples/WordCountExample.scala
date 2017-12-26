package com.roboxue.niffler.examples

import java.io.{File, PrintWriter}

import com.roboxue.niffler.{Niffler, Token}

import scala.io.Source

/**
  * @author rxue
  * @since 12/25/17.
  */
object WordCountExample extends Niffler {

  def main(args: Array[String]): Unit = {
    Niffler.init(args)
    try {
      this.syncRun(writeToFile)
    } finally {
      Niffler.terminate()
    }
  }

  // --- define all the tokens
  final val paragraphToProcess: Token[Iterator[String]] = Token("the paragraph to perform wordcount")
  final val wordCount: Token[Map[String, Int]] = Token("count by word")
  final val locationToWrite: Token[String] = Token("the filename to write")
  final val writeToFile: Token[Unit] = Token("write result to file")

  // -- provide implementation for each token
  $$(paragraphToProcess.dependsOn(Niffler.argv) usingFunction (argv => {
    Source.fromFile(argv.head).getLines()
  }))

  $$(wordCount.dependsOn(paragraphToProcess) usingFunction (paragraph => {
    paragraph
      .flatMap(_.split("\\s+"))
      .toSeq
      .groupBy(w => w)
      .mapValues(w => w.length)
  }))

  $$(locationToWrite.dependsOn(Niffler.argv) usingFunction (argv => {
    argv(1)
  }))

  $$(writeToFile.dependsOn(wordCount, locationToWrite) usingFunction ((map, location) => {
    val writer = new PrintWriter(new File(location))
    for ((key, value) <- map) {
      writer.println(s"$key -> $value")
    }
    writer.close()
  }))
}

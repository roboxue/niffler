Niffler helps reducing pull request size and risk of merge conflicts
-----------
- Less file touched & Smaller PR == reduced risk of merge conflicts 

Merge conflicts happens when two developers are touching the same source file. 
And the smaller the changelist, or the fewer files being touch, we are more likely to avoid merge conflicts.

### Why OOP could be a problem here
OOP requires an upfront design because once an interface/constructor has been written, 
and being implemented/called by a few other classes, updating those becomes a "merge conflict generator".
- updating constructor to ask for new data will modify every file that creates this class, 
and propagate the need of this new data to the callsite, a chain effect
- updating interface to ask for new data will modify every child class that implements it, 
regardless if they need this new param or not. It also propagate the need of this new data to the interface method's call site, another chain effect

Updating constructors and interface methods all yield to a chain effect that can easily impact 10s of production files and same amount of test files.
It is true that you can avoid these with a more careful and flexible upfront interface design, but it is HARD, 
especially if your app's use case isn't fully known when you started writing code, which tends to be the norm during Agile programming

You can think about the questions in another way: when my function `foo` receives five variable `bar1, bar2, bar3, bar4, bar5`,
 how many objects are being referenced here? 
 Likely `bar1` and `bar2` comes from the instance, `bar3` comes from a hashmap, `bar4` comes from a configuration object...
 Once you started thinking in this way, you'll suddenly realized these variables have travelled many source files in order to reach here.
 Adding a new variable will likely involve changing these files as well! 

### Why niffler can do better
Niffler encourages a flatter design by a separating the usage of data from the acquisition of data: 
- `Implementation` should only care about 1) what data is needed 2) what to do when data has arrived
- `Niffler` / `Logic` should only care about picking the right collection of `Implementation` to build a meaningful DAG,
 making sure each token has proper implementation that matches its description
- State is maintained explicitly using `ExecutionCache`
- No need to think about variable's life cycle. They will be there when you needed it, 
only because you have declared dependency on them
- During evaluation of an implementation, all the data that it needs has already been collected in the `ExectuionCache` of that invocation.
- `main function` / `call site` should only care about what token to invoke using which `Niffler` / `Logic`

In this way, new features can always be supported by new tokens and new implementations by modifying constant number of files

### Detailed example
Using an example of a simple project, I'd like to demonstrate how niffler helps reducing the size of the PR.

The source code using OOP is available at [here](https://github.com/roboxue/niffler/tree/306a8f5f97425a7c42e1435723284d1558ca76d3/example/src/main/scala/com/roboxue/niffler/examples/slimmer_pr_contorl)
The source code using Niffler is available at [here](https://github.com/roboxue/niffler/tree/306a8f5f97425a7c42e1435723284d1558ca76d3/example/src/main/scala/com/roboxue/niffler/examples/slimmer_pr)
The diff list using OOP is [this commit](https://github.com/roboxue/niffler/commit/3a93e1b43c80add4d07a33650f326bd4078490f6)
The diff list using Niffler is [this commit](https://github.com/roboxue/niffler/commit/b2c1b54c4894e92b4cc1cd0992391e8a43e3961d)

"Alice and Bob works on Charlie's team. They are writing a document comparison engine here, so the idea is Alice and Bob each will write many versions of relevance algorithm that will calculate two documents' similarity score."
Their repo currently has following folder layout:
```
EngineBase.scala // Shared code, result of a architecture design, maintained by Charlie
Engine1.scala // Alice's code
Engine2.scala // Bob's code
Engine3.scala // Bob's code
Engine4.scala // Alice's code
Application.scala // Shared code, maintained by Charlie
ArgsUtils.scala // Shared code, maintained by Charlie
```
```scala
// Interface.scala
trait EngineBase {
  /**
    * @param doc1 one document
    * @param doc2 another document
    * @return a score between 0 and 100 where 100 means most similar
    */
  def scoreDoc(doc1: String, doc2: String): Int
}

// ArgsUtils.scala
class ArgsUtils(args: Array[String]) {
  def file1: String = ... // ignored parsing code
  def file2: String = ... // ignored parsing code
}

// Application.scala
object Application {
  def main(args: Array[String]): Unit = {
    val engine1 = new Engine1()
    val engine2 = new Engine2()
    val engine3 = new Engine3()
    val engine4 = new Engine4()
    val parsedArgs = new ArgsUtils(args)
    val file1 = parsedArgs.file1
    val file2 = parsedArgs.file2
    val engines = Seq(engine1, engine2, engine3, engine4)
    val score = engines.map(eng => {
      eng.scoreDoc(file1, file2)
    }).sum.toDouble / engines.length
    println(s"avg score is $score")
  }
}

// Engine1.scala
class Engine1 extends EngineBase {
  override def scoreDoc(doc1: String, doc2: String): Int = {
    // ...magic 1
  }
}

// Engine2.scala
class Engine2 extends EngineBase {
  override def scoreDoc(doc1: String, doc2: String): Int = {
    // ...magic 2
  }
}

// Engine3.scala
class Engine3 extends EngineBase {
  override def scoreDoc(doc1: String, doc2: String): Int = {
    // ...magic 3
  }
}

// Engine4.scala
class Engine4 extends EngineBase {
  override def scoreDoc(doc1: String, doc2: String): Int = {
    // ...magic 4
  }
}
```
When Alice is working on improvements, she might want to use a Word Stemmer and a Stop Word List to power the enhancement. **During offline architecture discuesion**, Bob and Charlie agree that Stemmer could be added to the common interface, while stopWordList should be added to Engine1 locally since other Engine might not use it. In this case, `Interface.scala` and every `Engine*` file will be changed to accomondate the interface update, and `Engine1`'s constructor will be updated as well leading to a change in `Main.scala`

PR looks like [this commit](https://github.com/roboxue/niffler/commit/3a93e1b43c80add4d07a33650f326bd4078490f6)

```scala
// EngineBase.scala, inevitable to add stemmer to the contract
+ * @param stemmer a word stemmer
- def scoreDoc(doc1: String, doc2: String): Int
+ def scoreDoc(doc1: String, doc2: String, stemmer: Stemmer): Int

// ArgsUtils.scala
class ArgsUtils(args: Array[String]) {
  def file1: String = ... // ignored parsing code
  def file2: String = ... // ignored parsing code
+ def stemmer: Stemmer = ... // ignored implemention, unaviodable changes
+ def stopWordList: List[String] = ... // ignored implemention, unaviodable changes
}

// Application.scala
object Application {
  def main(args: Array[String]): Unit = {
+   val stopWordList = parsedArgs.stopWordList // avoidable if engine1 can create the stopWordList from args itself
+   val stemmer = parsedArgs.stemmer // avoidable if stemmer can be created by whoever needs it and cached properly
-   val engine1 = new Engine1()
+   val engine1 = new Engine1(stopWordList) // avoidable if there is no constructor
-   val score = engines.map(eng => eng.scoreDoc(file1, file2)).sum.toDouble / engines.length
+   val score = engines.map(eng => eng.scoreDoc(file1, file2, stemmer)).sum.toDouble / engines.length // avoidable if engine can create stemmers themselves
    println(s"score is $score")
  }
}

// Engine1.scala, changes here are unavoidable
class Engine1(stopWordList: List[String]) extends EngineBase {
- override def scoreDoc(doc1: String, doc2: String): Int = {
+ override def scoreDoc(doc1: String, doc2: String, stemmer: Stemmer): Int = {
*   // new implementation details in engine 1
  }
}

// Engine2.scala, changes could be avoided if there is no interface change
class Engine2 extends EngineBase {
- override def scoreDoc(doc1: String, doc2: String): Int = {
+ override def scoreDoc(doc1: String, doc2: String, stemmer: Stemmer): Int = {
    // ...implementation details in engine 2 unchanged
  }
}

// Engine3.scala and Engine4.scala are same as Engine2.scala
class Engine3 extends EngineBase {
- override def scoreDoc(doc1: String, doc2: String): Int = {
+ override def scoreDoc(doc1: String, doc2: String, stemmer: Stemmer): Int = {
    // ...implementation details in engine 3 unchanged
  }
}
class Engine4 extends EngineBase {
- override def scoreDoc(doc1: String, doc2: String): Int = {
+ override def scoreDoc(doc1: String, doc2: String, stemmer: Stemmer): Int = {
    // ...implementation details in engine 4 unchanged
  }
}
```

Avoidable changes are
- Engine2.scala
- Engine3.scala
- Engine4.scala
- Application.scala

Inevitable changes are
- EngineBase.scala
- Engine1.scala
- ArgsUtils.scala

Let's see how Niffler can help by eliminating the interface methods and constructors
```scala
// EngineBase.scala
trait EngineBase {
  this: Niffler =>
  import EngineBase._
  final val scoreDoc: Token[Int] = Token("a score between 0 and 100 where 100 means most similar")
  protected def scoreDocImpl: Implementation[Int]
  addImpl(scoreDocImpl)

  addImpl(file1.dependsOn(parsedArgs) { _.file1 })
  addImpl(file2.dependsOn(parsedArgs) { _.file2 })
  addImpl(parsedArgs.dependsOn(Niffler.argv) { (args) =>
    new ArgsUtils(args)
  })
}

object EngineBase extends Niffler {
  final val parsedArgs: Token[ArgsUtils] = Token("parsed argument")
  final val file1: Token[String] = Token("one document")
  final val file2: Token[String] = Token("another document")
}

// ArgsUtils.scala
class ArgsUtils(args: Array[String]) {
  def file1: String = "file1 content" // ignored actual parsing code, use dummy result instead
  def file2: String = "file2 content" // ignored actual parsing code, use dummy result instead
}

// Application.scala
object Application {
  def main(args: Array[String]): Unit = {
    Niffler.init(args) // making sure args is broadcast to every niffler in this jvm
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

// Engine1.scala
object Engine1 extends Niffler with EngineBase {
  import EngineBase._
  override def scoreDocImpl: Implementation[Int] = scoreDoc.dependsOn(file1, file2) {
    (file1, file2) =>
    // ...magic 1, shouldBe sometime smarter in production
    1
  }
}

// Engine2.scala
object Engine2 extends Niffler with EngineBase {
  import EngineBase._
  override def scoreDocImpl: Implementation[Int] = scoreDoc.dependsOn(file1, file2) {
    (file1, file2) =>
    // ...magic 2, shouldBe sometime smarter in production
    2
  }
}

// Engine3.scala
object Engine3 extends Niffler with EngineBase {
  import EngineBase._
  override def scoreDocImpl: Implementation[Int] = scoreDoc.dependsOn(file1, file2) {
    (file1, file2) =>
    // ...magic 3, shouldBe sometime smarter in production
    3
  }
}

// Engine4.scala
object Engine4 extends Niffler with EngineBase {
  import EngineBase._
  override def scoreDocImpl: Implementation[Int] = scoreDoc.dependsOn(file1, file2) {
    (file1, file2) =>
    // ...magic 4, shouldBe sometime smarter in production
    4
  }
}
```

In the PR stage, only stemmer changes go to EngineBase, and whatever crazy thing Alice does stays in Engine1.scala only
PR is way smaller now! [this commit](https://github.com/roboxue/niffler/commit/b2c1b54c4894e92b4cc1cd0992391e8a43e3961d)
```scala
// ArgsUtils.scala
class ArgsUtils(args: Array[String]) {
+ def stemmer: Stemmer = ... // ignored implemention, unaviodable changes
+ def stopWordList: List[String] = ... // ignored implemention, unaviodable changes
}

// EngineBase.scala
trait EngineBase {
+ addImpl(stemmer.dependsOn(parsedArgs) { _.stemmer })
}

object EngineBase {
+ final val stemmer: Token[Stemmer] = Token("a word stemmer")
}

// Engine1.scala
object Engine1 extends Niffler with EngineBase {
+ final val stopWordList: Token[List[String]] = Token("a stop word list marking common vocabulary that should be ignored")
+ addImpl(stopWordList.dependsOn(EngineBase.parsedArgs) { _.stopWordList })
-  override def scoreDocImpl: Implementation[Int] = scoreDoc.dependsOn(file1, file2, stemmer) {
+  override def scoreDocImpl: Implementation[Int] = scoreDoc.dependsOn(file1, file2, stemmer, stopWordList) {
-   (file1, file2) =>
+   (file1, file2, stemmer, stopWordList) =>
*   // ...magic 1 changed
  }
}
```
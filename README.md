# Niffler: Dataflow programming for Scala

**Niffler** is a [Dataflow programming](https://en.wikipedia.org/wiki/Dataflow_programming) library for Scala. It's a set of lightweight DSL that encourages developer to write application logic in [Pure functions](https://en.wikipedia.org/wiki/Pure_function) and assembly these logic fragments into executable [DAGs](https://en.wikipedia.org/wiki/Directed_acyclic_graph) in runtime.

### Basic concepts
> Algorithms + Data Structures = Programs [Niklaus Wirth](https://en.wikipedia.org/wiki/Algorithms_%2B_Data_Structures_%3D_Programs)

- `Token[T]` is the base unit of "data". Similar to declarations like `val a: Int`, it has all meta info related to a data, and can be used to fetch the actual value from an `ExecutionCache`
- `ExecutionCache` is where data being stored. It's effectively a `Map[Token[Any], Any]` with garanteed type consistancy among it's entries.
- `Implementation[T]` is the base unit of "algorithm/function". Implementation binds to a `Token[T]`, takes a list of other `Token[_]` as dependency, and a pure function to construct. Treated as a `def foo(params...)` that can be evaluated with a `ExecutionCache` and yield `T`, it will fetch the dependency's value from cache in runtime, run them using it's own code and write the result back to the cache with the token it binds to.g
- `Logic` and `Niffler` are all collection of `Implementation`. `Niffler` is privately mutable, while `Logic` is always immutable.
- To eval an `Implementation`, you can do `logic.syncRun(someToken)`. This will create a DAG using `Implementations` in the `Logic`, evaluated every token whose dependency has been met, until the desired token has been evaluated.
- `logic.asyncRun(someToken)` is also available as you might have guessed
- `ExecutionCache` is retured as part of the return value of an execution. To reuse a cache, do `logic.syncRun(someToken, cache=someCache)`.

##### Tutorial
see [NifflerSyntaxDemo.scala](example/src/main/scala/com/roboxue/niffler/examples/NifflerSyntaxDemo.scala)

### Niffler aims for...
##### Better performance on complex logic
- Concurrent exeuction, fully automated: Your application/`Niffler` is basically a collection of `Implementation`s, which will automatically form a [DAG](https://en.wikipedia.org/wiki/Directed_acyclic_graph). This means Niffler will concurrently trigger the evaluation of `Implementation` whenever its dependency has been met. The following [example1](#example-1) demos the very basic idea of it.
  > There will be a huge gain if your code is previously single threaded executed, or if executed remotely via RPC calls, like Spark operation. This gain might be trival or even negative however, because of the DAG setup overhead, if your logic is very simple or the execution relis heavily on local CPU resources. But there are still many other reasons that Niffler might be useful in these situations

- Better scheduling: instead of launching jobs with commandline arguments, and scheduling using a crontab, Niffler allows you to start a server, and trigger different main functions in your jar on demand via REST endpoints or UI.
  > This might be trival again if your application is simple. But if your repo is a collection of complex datapipelines, and/or they are currently sharing status like failed/succeeded using file system, give Niffler a try. Niffler eliminates the need of a standalone scheduling system, and provides the state sharing utilities from `ExecutionCache`

##### Better monitoring
- Out-of-box web UI: There is an optional out-of-box utility server that can be brought up with one line. This UI will display every Niffler DAG's execution status, allowing you to visually inspect which part of your logic is throwing exceptions and how your DAG is executed in parallel.
- Automatic performance metrics: Remeber the times you need to do timings like `println("this operation took ${endTime - startTime} millis")`? Since there is a clear logic boundry among `Implementation`, this is done automatically by Niffler now.

##### Better maintainance
- Maximim code reuse and testability: Break application logic down into `Implementation`([Pure functions](https://en.wikipedia.org/wiki/Pure_function)), so that every line of code in this function is reusable and testable
- DRY up your comments: don't repeat yourself even when commenting. Doc string belongs to `Tokens`. The moment your `Implementation` referenced other `Token`s, their doc-string follows as well. See [example2](#example-2)

##### Better teamwork
- Reduce pull request diff size: With Niffler, it is totally possible to get rid of abstract method declaration in interfaces and class constructors, the major reason why there is merge conflicts during teamwork. (Details explaned [later](#niffler-reduces-pull-request-size))
- Self graphic doc: Tired of drawing a flowchart to demonstate your dataflow which will be out-of-date the moment it has been created? Automated DAG means self-graphic-doc


#### Example 1
Concurrent execution
```scala
    import com.roboxue.niffler.{Logic, Token}
    // these lines help self-doc
    val tA = Token[Int]("this will print 'A' and return 1")
    val tB = Token[Int]("this will print 'B' and return 2")
    val tC = Token[Int]("this will print 'C 1+2' and return 3")
    // these lines assemble the logics
    val logic = Logic(Seq(
      tA.assign({ Thread.sleep(1); print("A"); 1 }),
      tB.assign({ Thread.sleep(1); print("B"); 2 }),
      tC.dependsOn(tA, tB) {
        (resultOfA: Int, resultOfB: Int) =>
          println(s"C: $resultOfA + $resultOfB")
          resultOfA + resultOfB
      }
    ))
    // these lines trigger the execution
    // you'll see 'ABC 1+2' on screen or 'BAC 1+2' randomly
    for (_ <- Range(0, 100)) {
      assert(logic.syncRun(tC).result == 3)
    }
```

##### Example 2
Saving doc strings
Niffler doesn't try to replace well designed scaladoc. By outputing DAG graph automatically, it aims to improve the documentation quality by reducing the "degree of freedom" for the docs.
In the example below, becuase every `Token` being refereneced when writing niffler `Implementation` has been documented upon creation, we 'DRY'ed up two redudant doc strings.

Before:
```scala
    /**
      * @param p1 p1Description
      * @param p2 p2Description
      * @return myWorkDescription
      */
	def myWork(p1: String, p2: Int): Boolean

    /**
      * @param p1 p1Description
      * @param p2 p2Description
      * @return myOtherWorkDescription
      */
	def myOtherWork(p1: String, p2: Int): Char

    // we are writing 6 lines of docstring now
    // note that p1Description and p2Description has appeared twice, and potentially many more times elsewhere
    // and these docstrings can only be string literal, it cannot be programmatically generated
    // let's fix this in Niffler
```
After:
```scala
    // doc string binds to Token
	val p1 = Token[String]("p1Description")
	val p2 = Token[Int]("p2Description")
    // doc string can be programmicatlly generated!
    val myWork = Token[Boolean](s"myWorkDescription uses ${p1.name}")
    val myOtherWork = Token[Boolean]("myOtherWorkDescription")
    // only 4 lines of doc strings, and that is really the minimum you can get

    // doc free implementation, add comments only when needed
    myWork dependsOn(p1, p2) {...}
    myOtheWwork dependsOn(p1, p2) {...}
```

### Niffler reduces pull request size
Merge conflicts happens when two developers are touching the same source file. And the smaller the changelist, or the fewer files being touch, we are more likely to aviod merge conflicts.

Using an example of a simple project, I'd like to demonstrate how niffler helps reducing the size of the PR.

"Alice and Bob works on Charlie's team. They are writing a document compairison engine here, so the idea is Alice and Bob each will write many versions of relevance algorithm that will calculate two documents' similarity score."
Their repo currently has following folder layout:
```
Interface.scala // Shared code, result of a architecture design, maintained by Charlie
Engine1.scala // Alice's code
Engine2.scala // Bob's code
Engine3.scala // Bob's code
Engine4.scala // Alice's code
Main.scala // Shared code, maintained by Charlie
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
class ArgsUtils(args: String) {
  def file1: String = ... // ignored parsing code
  def file2: String = ... // ignored parsing code
}

// Main.scala
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

PR looks like
```scala
// Interface.scala, aviodable if the interface doesn't have parameter list
+ * @param stemmer a word stemmer
- def scoreDoc(doc1: String, doc2: String): Int
+ def scoreDoc(doc1: String, doc2: String, stemmer: Stemmer): Int

// ArgsUtils.scala
class ArgsUtils(args: String) {
  def file1: String = ... // ignored parsing code
  def file2: String = ... // ignored parsing code
+ def stemmer: Stemmer = ... // ignored implemention, unaviodable changes
+ def stopWordList: List[String] = ... // ignored implemention, unaviodable changes
}

// Main.scala
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
*   // ...magic 1 changed
  }
}

// Engine2.scala, changes could be avoided if there is no interface change
class Engine2 extends EngineBase {
- override def scoreDoc(doc1: String, doc2: String): Int = {
+ override def scoreDoc(doc1: String, doc2: String, stemmer: Stemmer): Int = {
    // ...magic 2 unchanged
  }
}

// Engine3.scala and Engine4.scala are same as Engine2.scala
```

Avoidable changes are
- Engine2.scala
- Engine3.scala
- Engine4.scala
- Interface.scala
- Main.scala

Unavioable changes are
- Engine1.scala
- ArgsUtils.scala

Let's see how Niffler can help by eliminating the interface methods and constructors
```scala
// Interface.scala
trait EngineBase {
  this: Niffler =>
  final val file1: Token[String] = Token("one document")
  final val file2: Token[String] = Token("another document")
  final val scoreDoc: Token[Int] = Token("a score between 0 and 100 where 100 means most similar")
  final val initializeCache: Token[Unit] = Token("initalize cache to store some reuseable data")
  protected def scoreDocImpl: Implementation[Int]

  addImpl(scoreDocImpl)
  addImpl(initializeCache.dependsOn(file1, file2) {
  	(_, _) => // no op
  })
  addImpl(file1.dependsOn(Niffler.argv) {
    args => parseArgsUtils(args).file1
  })
  addImpl(file2.dependsOn(Niffler.argv) {
    args => parseArgsUtils(args).file2
  })
}

// Main.scala
object Application {
  def main(args: Array[String]): Unit = {
    Niffler.init(args) // making sure args is broadcasted to every niffler in this jvm
    try {
      val engines = Seq(Engine1, Engine2, Engine3, Engine4) // a Seq[Niffler with EngineBase]
      val cacheInitializer = new Niffler with EngineBase // we create a Niffler with EngineBase to utilize the initializeCache command
      val sharedCache = cacheInitializer.syncRun(cacheInitialilzer.initializeCache).cache // this cache contains the value of file1 and file2 now
      val score = engines.map(eng => {
        eng.syncRun(eng.scoreDoc, cache = sharedCache).result // using sharedCache, file parsing is saved
      }).sum.toDouble / engines.length
      println(s"avg score is $score")
    } finally {
	  Niffler.termiate()
    }
  }
}

// Engine1.scala
object Engine1 extends Niffler with EngineBase {
  override def scoreDocImpl: Implementation[Int] = scoreDoc.dependsOn(file1, file2) {
    (file1, file2) =>
    // ...magic 1
  }
}

// Engine2.scala
object Engine2 extends Niffler with EngineBase {
  override def scoreDocImpl: Implementation[Int] = scoreDoc.dependsOn(file1, file2) {
    (file1, file2) =>
    // ...magic 2
  }
}

// Engine3.scala
object Engine3 extends Niffler with EngineBase {
  override def scoreDocImpl: Implementation[Int] = scoreDoc.dependsOn(file1, file2) {
    (file1, file2) =>
    // ...magic 3
  }
}

// Engine4.scala
object Engine4 extends Niffler with EngineBase {
  override def scoreDocImpl: Implementation[Int] = scoreDoc.dependsOn(file1, file2) {
    (file1, file2) =>
    // ...magic 4
  }
}
```

In the PR stage, there is no more offline architecture discussion needed about those interface changes, and whatever crazy thing Alice does stays in Engine1.scala only
PR is way smaller now!
```scala
// ArgsUtils.scala
class ArgsUtils(args: String) {
  def file1: String = ... // ignored parsing code
  def file2: String = ... // ignored parsing code
+ def stemmer: Stemmer = ... // ignored implemention, unaviodable changes
+ def stopWordList: List[String] = ... // ignored implemention, unaviodable changes
}


// Engine1.scala
- object Engine1 extends Niffler with EngineBase {
+ object Engine1 extends Niffler with EngineBase with NeedStemmer with NeedStopWordList {
-  override def scoreDocImpl: Implementation[Int] = scoreDoc.dependsOn(file1, file2, stemmer) {
+  override def scoreDocImpl: Implementation[Int] = scoreDoc.dependsOn(file1, file2, stemmer, stopWordList) {
-   (file1, file2) =>
+   (file1, file2, stemmer, stopWordList) =>
*   // ...magic 1 changed
  }
}

+trait NeedStemmer {
+  this: Niffler =>
+  final val stemmer: Token[Stemmer] = Token("create a stemmer")
+  addImpl(stemmer.dependsOn(Niffler.argv) {
+    (args) => new ArgsUtils(args).stemmer
+  })
+}

+trait NeedStopWordList {
+  this: Niffler =>
+  final val stopWordList: Token[List[String]] = Token("create a stop word list")
+  addImpl(stopWordList.dependsOn(Niffler.argv) {
+    (args) => new ArgsUtils(args).stopWordList
+  })
+}
```
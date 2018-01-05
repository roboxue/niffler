# Niffler: Dataflow programming for Scala
<strong>Self optimize, Self document, Self monitor</strong>

> "Let's be greedy" 

![niffler](https://78.media.tumblr.com/79cbce85198fb94f302ed8f7b47fa394/tumblr_inline_oivo7hMSOA1qbxxlx_500.gif)

[![Build Status](https://travis-ci.org/roboxue/niffler.svg?branch=master)](https://travis-ci.org/roboxue/niffler)

### Install
published to Maven Central and cross-built for Scala 2.11 and 2.12, so you can just add the following to your build.sbt

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.roboxue/niffler-core_2.11/badge.svg?subject=niffler_2.11)](https://maven-badges.herokuapp.com/maven-central/com.roboxue/niffler-core_2.11)  
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.roboxue/niffler-core_2.12/badge.svg?subject=niffler_2.12)](https://maven-badges.herokuapp.com/maven-central/com.roboxue/niffler-core_2.12)  
```sbtshell
val nifflerVersion = "0.2.0" // for latest version see the badges above

libraryDependencies ++= Seq(
  "com.roboxue" %% "niffler-core",
  "com.roboxue" %% "niffler-monitoring" // optional
).map(_ % nifflerVersion)

```

**Niffler** is a [Dataflow programming](https://en.wikipedia.org/wiki/Dataflow_programming) library for Scala. 
It's a set of lightweight DSL that encourages developer to write application logic in [Pure functions](https://en.wikipedia.org/wiki/Pure_function) 
and assembly these logic fragments into executable [DAGs](https://en.wikipedia.org/wiki/Directed_acyclic_graph) in runtime.
By using Niffler, you can easily write app that
- `self-optimize`
    - complex logic, assembled by `Implementations`, will execute in parallel whenever possible
    - write your `Implementation` in any order as long as they don't bind to the same `Token`. Otherwise FIFO rule applies
- `self-doc`
    - visualize application logic in runtime, because it is a DAG
    - options to dump svg files for `Object` that extends `Niffler` trait
    - doc string are binded to `Token`, which means you don't have to repeatedly document your dependency/parameter list anymore
- `self-monitor`
    - view the progress of this execution when the app is running
    - options to automatically log the execution time for each `Implementation` in the `Logic`
    - unhandled exceptions can be pin pointed out

### Basic concepts
> Algorithms + Data Structures = Programs [Niklaus Wirth](https://en.wikipedia.org/wiki/Algorithms_%2B_Data_Structures_%3D_Programs)

- `Token[T]` is the base unit of "data". Similar to declarations like `val a: Int`, it has all meta info related to a data, and can be used to fetch the actual value from an `ExecutionCache`
- `ExecutionCache` is where data being stored. It's effectively a `Map[Token[Any], Any]` with garanteed type consistancy among it's entries.
- `Implementation[T]` is the base unit of "algorithm/function". Implementation binds to a `Token[T]`, takes a list of other `Token[_]` as dependency, and a pure function to construct. Treated as a `def foo(params...)` that can be evaluated with a `ExecutionCache` and yield `T`, it will fetch the dependency's value from cache in runtime, run them using it's own code and write the result back to the cache with the token it binds to.
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
- Out-of-box web UI: [example](#example-3) There is an optional out-of-box utility server that can be brought up with one line. This UI will display every Niffler DAG's execution status, allowing you to visually inspect which part of your logic is throwing exceptions and how your DAG is executed in parallel.
- Automatic performance metrics: Remeber the times you need to do timings like `println("this operation took ${endTime - startTime} millis")`? Since there is a clear logic boundry among `Implementation`, this is done automatically by Niffler now.

##### Better maintainance
- Maximim code reuse and testability: Break application logic down into `Implementation`([Pure functions](https://en.wikipedia.org/wiki/Pure_function)), so that every line of code in this function is reusable and testable
- DRY up your comments: don't repeat yourself even when commenting. Doc string belongs to `Tokens`. The moment your `Implementation` referenced other `Token`s, their doc-string follows as well. See [example2](#example-2)

##### Better teamwork
- Reduce pull request diff size: With Niffler, it is totally possible to get rid of abstract method declaration in interfaces and class constructors, the major reason why there is merge conflicts during teamwork. 
(Details explained [here](md/pull_request_reduction.md))
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

##### Example 3
Automatical list the topology of the `Logic`, and record the execution time for each `Token` in the `Logic`
![image](https://user-images.githubusercontent.com/4080835/34626160-3d12bbfc-f221-11e7-9496-194b9c78d58c.png)

Pin point where is the exception 
![image](https://user-images.githubusercontent.com/4080835/34626952-54d40392-f224-11e7-9e9a-6e14f59722d8.png)

Live view of on going executions, know where it stucks, no maigc
![image](https://user-images.githubusercontent.com/4080835/34626898-287463a0-f224-11e7-9ab8-8750bb6846f7.png)

##### Housekeeping
To release, make sure pgp key and sonatype credential is in the correct location, then execute 
- `sbt "release with-defaults"` for minor version updates
- `sbt release` for major version updates

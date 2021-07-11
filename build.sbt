import sbtcrossproject.CrossPlugin.autoImport.{crossProject, CrossType}
import scala.sys.process._

organization in ThisBuild := "me.shadaj"

lazy val scala212Version = "2.12.13"
lazy val scala213Version = "2.13.5"
lazy val supportedScalaVersions = List(scala212Version, scala213Version)

scalaVersion in ThisBuild := scala213Version

lazy val scalapy = project.in(file(".")).aggregate(
  macrosJVM, macrosNative,
  coreJVM, coreNative,
).settings(
  publish := {},
  publishLocal := {}
)

addCommandAlias(
  "publishSignedAll",
  (scalapy: ProjectDefinition[ProjectReference])
    .aggregate
    .map(p => s"+ ${p.asInstanceOf[LocalProject].project}/publishSigned")
    .mkString(";", ";", "")
)

lazy val macros = crossProject(JVMPlatform, NativePlatform)
  .crossType(CrossType.Pure)
  .in(file("coreMacros"))
  .settings(
    name := "scalapy-macros",
    crossScalaVersions := supportedScalaVersions,
    libraryDependencies += "org.scala-lang" % "scala-reflect" % scalaVersion.value
  )

lazy val macrosJVM = macros.jvm
lazy val macrosNative = macros.native

lazy val pythonLdFlags = {
  val withoutEmbed = "python3-config --ldflags".!!
  if (withoutEmbed.contains("-lpython")) {
    withoutEmbed.split(' ').map(_.trim).filter(_.nonEmpty).toSeq
  } else {
    val withEmbed = "python3-config --ldflags --embed".!!
    withEmbed.split(' ').map(_.trim).filter(_.nonEmpty).toSeq
  }
}

lazy val pythonLibsDir = {
  pythonLdFlags.find(_.startsWith("-L")).get.drop("-L".length)
}

lazy val core = crossProject(JVMPlatform, NativePlatform)
  .in(file("core"))
  .dependsOn(macros)
  .settings(
    name := "scalapy-core",
    crossScalaVersions := supportedScalaVersions,
    sourceGenerators in Compile += Def.task {
      val fileToWrite = (sourceManaged in Compile).value / "TupleReaders.scala"
      val methods = (2 to 22).map { n =>
        val tupleElements = (1 to n).map(t => s"r$t.read(orArr(${t - 1}))")
          .mkString(", ")
        s"""implicit def tuple${n}Reader[${(1 to n).map(t => s"T$t").mkString(", ")}](implicit ${(1 to n).map(t => s"r$t: Reader[T$t]").mkString(", ")}): Reader[(${(1 to n).map(t => s"T$t").mkString(", ")})] = {
           |  new Reader[(${(1 to n).map(t => s"T$t").mkString(", ")})] {
           |    override def read(or: PyValue) = {
           |      val orArr = or.getTuple
           |      ($tupleElements)
           |    }
           |  }
           |}"""
      }

      val toWrite =
        s"""package me.shadaj.scalapy.readwrite
           |import me.shadaj.scalapy.interpreter.PyValue
           |trait TupleReaders {
           |${methods.mkString("\n")}
           |}""".stripMargin

      IO.write(fileToWrite, toWrite)
      Seq(fileToWrite)
    },
    sourceGenerators in Compile += Def.task  {
      val fileToWrite = (sourceManaged in Compile).value / "TupleWriters.scala"
      val methods = (2 to 22).map { n =>
        val seqArgs = (1 to n).map(t => s"r$t.write(v._" + t + ")").mkString(", ")
        s"""implicit def tuple${n}Writer[${(1 to n).map(t => s"T$t").mkString(", ")}](implicit ${(1 to n).map(t => s"r$t: Writer[T$t]").mkString(", ")}): Writer[(${(1 to n).map(t => s"T$t").mkString(", ")})] = {
           |  new Writer[(${(1 to n).map(t => s"T$t").mkString(", ")})] {
           |    override def write(v: (${(1 to n).map(t => s"T$t").mkString(", ")})): PyValue = {
           |      CPythonInterpreter.createTuple(Seq(${seqArgs}))
           |    }
           |  }
           |}"""
      }

      val toWrite =
        s"""package me.shadaj.scalapy.readwrite
           |import me.shadaj.scalapy.interpreter.{CPythonInterpreter, PyValue}
           |trait TupleWriters {
           |${methods.mkString("\n")}
           |}""".stripMargin

      IO.write(fileToWrite, toWrite)
      Seq(fileToWrite)
    },
    sourceGenerators in Compile += Def.task {
      val fileToWrite = (sourceManaged in Compile).value / "FunctionReaders.scala"
      val methods = (0 to 22).map { n =>
        val functionArgs = (1 to n).map(t => s"w$t.write(i$t)")
          .mkString(", ")
        s"""implicit def function${n}Reader[${((1 to n).map(t => s"T$t") :+ "O").mkString(", ")}](implicit ${((1 to n).map(t => s"w$t: Writer[T$t]") :+ "oReader: Reader[O]").mkString(", ")}): Reader[(${(1 to n).map(t => s"T$t").mkString(", ")}) => O] = {
           |  new Reader[(${(1 to n).map(t => s"T$t").mkString(", ")}) => O] {
           |    override def read(orig: PyValue) = {
           |      (${(1 to n).map(t => s"i$t: T$t").mkString(", ")}) => {
           |        oReader.read(CPythonInterpreter.call(orig, Seq($functionArgs), Seq()))
           |      }
           |    }
           |  }
           |}"""
      }

      val toWrite =
        s"""package me.shadaj.scalapy.readwrite
           |import me.shadaj.scalapy.interpreter.{CPythonInterpreter, PyValue}
           |trait FunctionReaders {
           |${methods.mkString("\n")}
           |}""".stripMargin

      IO.write(fileToWrite, toWrite)
      Seq(fileToWrite)
    },
    sourceGenerators in Compile += Def.task  {
      val fileToWrite = (sourceManaged in Compile).value / "FunctionWriters.scala"
      val methods = (0 to 22).map { n =>
        val seqArgs = (1 to n).map(t => s"r$t.read(args(${t - 1}))").mkString(", ")
        s"""implicit def function${n}Writer[${((1 to n).map(t => s"T$t") :+ "O").mkString(", ")}](implicit ${((1 to n).map(t => s"r$t: Reader[T$t]") :+ "oWriter: Writer[O]").mkString(", ")}): Writer[(${(1 to n).map(t => s"T$t").mkString(", ")}) => O] = {
           |  new Writer[(${(1 to n).map(t => s"T$t").mkString(", ")}) => O] {
           |    override def write(v: (${(1 to n).map(t => s"T$t").mkString(", ")}) => O): PyValue = {
           |      CPythonInterpreter.createLambda(args => oWriter.write(v($seqArgs)))
           |    }
           |  }
           |}"""
      }

      val toWrite =
        s"""package me.shadaj.scalapy.readwrite
           |import me.shadaj.scalapy.interpreter.{CPythonInterpreter, PyValue}
           |trait FunctionWriters {
           |${methods.mkString("\n")}
           |}""".stripMargin

      IO.write(fileToWrite, toWrite)
      Seq(fileToWrite)
    },
    libraryDependencies += "org.scala-lang.modules" %%% "scala-collection-compat" % "2.5.0",
    libraryDependencies += "org.scala-lang" % "scala-reflect" % scalaVersion.value,
    libraryDependencies += "org.scalatest" %%% "scalatest" % "3.2.9" % Test,
    unmanagedSourceDirectories in Compile += {
      val sharedSourceDir = (baseDirectory in ThisBuild).value / "core/shared/src/main"
      if (scalaVersion.value.startsWith("2.13.")) sharedSourceDir / "scala-2.13"
      else sharedSourceDir / "scala-2.11_2.12"
    }
  ).jvmSettings(
    libraryDependencies += "net.java.dev.jna" % "jna" % "5.8.0",
    fork in Test := true,
    javaOptions in Test += s"-Djna.library.path=$pythonLibsDir"
  ).nativeSettings(
    nativeLinkStubs := true,
    nativeLinkingOptions ++= pythonLdFlags
  )

lazy val coreJVM = core.jvm
lazy val coreNative = core.native

lazy val facadeGen = project.in(file("facadeGen"))
  .dependsOn(coreJVM)
  .settings(
    fork in run := true,
    javaOptions in run += s"-Djna.library.path=${"python3-config --prefix".!!.trim}/lib"
  )

lazy val docs = project
  .in(file("built-docs"))
  .settings(
    moduleName := "built-docs",
  )
  .enablePlugins(MdocPlugin, DocusaurusPlugin)
  .dependsOn(coreJVM)
  .settings(
    fork := true,
    connectInput := true,
    javaOptions += s"-Djna.library.path=$pythonLibsDir",
    docusaurusCreateSite := {
      mdoc.in(Compile).toTask(" ").value
      Process(List("yarn", "install"), cwd = DocusaurusPlugin.website.value).!
      Process(List("yarn", "run", "build"), cwd = DocusaurusPlugin.website.value).!
      val out = DocusaurusPlugin.website.value / "build"
      out
    }
  )

lazy val bench = crossProject(JVMPlatform, NativePlatform)
  .crossType(CrossType.Pure)
  .in(file("bench"))
  .settings(
    name := "scalapy-bench",
    version := "0.1.0-SNAPSHOT"
  ).jvmSettings(
    javaOptions += s"-Djna.library.path=$pythonLibsDir"
  ).nativeSettings(
    nativeLinkingOptions ++= pythonLdFlags,
    nativeMode := "release-fast"
  ).dependsOn(core)

lazy val benchJVM = bench.jvm
lazy val benchNative = bench.native

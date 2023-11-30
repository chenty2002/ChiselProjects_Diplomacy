ThisBuild / scalaVersion     := "2.12.10"
ThisBuild / version          := "0.1.0"
// chisel 3.3.3 too old for chiselfv
lazy val root = (project in file("."))
  .settings(
    name := "Diplomacy",
    libraryDependencies ++= Seq(
      "edu.berkeley.cs" %% "chisel3" % "3.3.3",
      "edu.berkeley.cs" %% "chiseltest" % "0.3.1" % "test",
      "edu.berkeley.cs" %% "rocketchip" % "1.2.+"

    ),
    scalacOptions ++= Seq(
      "-Xsource:2.11",
      "-language:reflectiveCalls",
      "-deprecation",
      "-feature",
      "-Xcheckinit"
    ),
    addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.full)
  )
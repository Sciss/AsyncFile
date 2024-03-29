lazy val baseName  = "AsyncFile"
lazy val baseNameL = baseName.toLowerCase

lazy val projectVersion = "0.2.1"
lazy val mimaVersion    = "0.2.0"

lazy val deps = new {
  val main = new {
    val dom       = "1.2.0"
    val log       = "0.1.1"
    val model     = "0.3.5"
  }
  val test = new {
    val scalaTest = "3.2.10"
  }
}

lazy val commonJvmSettings = Seq(
  crossScalaVersions := Seq("3.0.2", "2.13.6", "2.12.15"),
)

ThisBuild / version       := projectVersion
ThisBuild / organization  := "de.sciss"
ThisBuild / versionScheme := Some("pvp")

lazy val root = crossProject(JSPlatform, JVMPlatform).in(file("."))
  .enablePlugins(BuildInfoPlugin)
  .jvmSettings(commonJvmSettings)
  .settings(
    name               := baseName,
    scalaVersion       := "2.13.6",
    description        := "A library to read and write files asynchronously on the JVM and JS",
    homepage           := Some(url(s"https://github.com/Sciss/${name.value}")),
    licenses           := Seq("AGPL v3+" -> url("http://www.gnu.org/licenses/agpl-3.0.txt")),
    mimaPreviousArtifacts := Set("de.sciss" %% baseNameL % mimaVersion),
    console / initialCommands := """import de.sciss.synth.io._""",
    libraryDependencies ++= Seq(
      "de.sciss"      %%% "log"       % deps.main.log,
      "de.sciss"      %%% "model"     % deps.main.model,
      "org.scalatest" %%% "scalatest" % deps.test.scalaTest % Test,
    ),
    scalacOptions ++= Seq(
      "-deprecation", "-unchecked", "-feature", "-encoding", "utf8", "-Xlint", "-Xsource:2.13",
    ),
    Compile / compile / scalacOptions ++= {
      val jdkGt8  = scala.util.Properties.isJavaAtLeast("9")
      val sv      = scalaVersion.value
      val isDotty = sv.startsWith("3.") // https://github.com/lampepfl/dotty/issues/8634
      val sq0     = if (!isDotty && jdkGt8) List("-release", "8") else Nil
      if (sv.startsWith("2.12.")) sq0 else "-Wvalue-discard" :: sq0
    }, // JDK >8 breaks API; skip scala-doc
    // ---- build info ----
    buildInfoKeys := Seq(name, organization, version, scalaVersion, description,
      BuildInfoKey.map(homepage) { case (k, opt)           => k -> opt.get },
      BuildInfoKey.map(licenses) { case (_, Seq((lic, _))) => "license" -> lic }
    ),
    buildInfoPackage := "de.sciss.asyncfile"
  )
  .jsSettings(
    libraryDependencies ++= Seq(
      "org.scala-js" %%% "scalajs-dom" % deps.main.dom,
    ),
  )
  .settings(publishSettings)


// ---- publishing ----
lazy val publishSettings = Seq(
  publishMavenStyle := true,
  Test / publishArtifact := false,
  pomIncludeRepository := { _ => false },
  developers := List(
    Developer(
      id    = "sciss",
      name  = "Hanns Holger Rutz",
      email = "contact@sciss.de",
      url   = url("https://www.sciss.de")
    )
  ),
  scmInfo := {
    val h = "github.com"
    val a = s"Sciss/${name.value}"
    Some(ScmInfo(url(s"https://$h/$a"), s"scm:git@$h:$a.git"))
  },
)


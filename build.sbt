import com.typesafe.sbt.SbtGhPages.ghpages
import com.typesafe.sbt.SbtGit.git
import com.typesafe.sbt.SbtSite.site
import sbtrelease._
import sbtrelease.ReleasePlugin.ReleaseKeys._
import sbtrelease.ReleaseStateTransformations._
import SonatypeKeys._

name := "beamly-core-scalaz"

organization := "com.beamly"

scalaVersion := "2.11.2"

crossScalaVersions := Seq("2.11.2", "2.10.4")

scalacOptions := Seq("-optimize", "-deprecation", "-unchecked", "-encoding", "utf8", "-Yinline-warnings", "-target:jvm-1.6", "-feature", "-Xlint", "-Ywarn-value-discard")

fork in Test := true

libraryDependencies <++= scalaVersion { sv =>
  Seq(
    "com.beamly" %% "beamly-core-lang" % "0.5.0",
    "org.scalaz" %% "scalaz-core" % "7.1.0",
    "org.specs2" %% "specs2" % "2.4.1" % "test"
  )
}

incOptions := CrossVersion partialVersion scalaVersion.value collect {
  case (2, scalaMajor) if scalaMajor >= 11 => incOptions.value withNameHashing true
} getOrElse incOptions.value // name hashing causes StackOverflowError under sbt 0.13.5 & scala 2.10.4, see sbt#1237 & SI-8486

credentials += Credentials(Path.userHome / ".sbt" / ".zeebox_credentials")

publishMavenStyle := true

licenses := Seq("Apache License, Version 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt"))

homepage := Some(url("http://beamly.github.io/beamly.core.scalaz/"))

pomExtra :=
  <scm>
    <url>git@github.com:beamly/beamly.core.scalaz.git</url>
    <connection>scm:git:git@github.com:beamly/beamly.core.scalaz.git</connection>
  </scm>
  <developers>
    <developer>
      <id>derekjw</id>
      <name>Derek Williams</name>
      <url>https://github.com/derekjw</url>
    </developer>
    <developer>
      <id>agustafson</id>
      <name>Andrew Gustafson</name>
      <url>https://github.com/agustafson</url>
    </developer>
    <developer>
      <id>glenford</id>
      <name>Glen Ford</name>
      <url>https://github.com/glenford</url>
    </developer>
    <developer>
      <id>dwijnand</id>
      <name>Dale Wijnand</name>
      <url>https://github.com/dwijnand</url>
    </developer>
  </developers>

// Import default settings. This changes `publishTo` settings to use the Sonatype repository and adds several commands for publishing.
sonatypeSettings

aetherSettings

releaseSettings

/**
 * Release versioning:
 * All minor releases have a bugfix version of 0.
 * To create a new bugfix release, checkout the v{x}.{y}.0 tagged release as branch v{x}.{y}.
 * All bugfix releases for that minor version should be created from that branch. The bugfix
 * version should automatically increment within that branch.
 */
releaseVersion := { ver =>
  Version(ver) map { v =>
    v.copy(bugfix = v.bugfix map (_ max 1) orElse Some(0)).withoutQualifier.string
  } getOrElse versionFormatError
}

nextVersion    := { ver =>
  Version(ver) map { v =>
    v.bugfix collect {
      case n if n > 0 => v.bumpBugfix.string
    } getOrElse {
      v.bumpMinor.copy(bugfix = None).asSnapshot.string
    }
  } getOrElse versionFormatError
}

releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runTest,
  setReleaseVersion,
  commitReleaseVersion,
  tagRelease,
  publishArtifacts.copy(action = PublishSignedOps.publishSignedAction),
  setNextVersion,
  commitNextVersion,
  pushChanges
)

site.settings

ghpages.settings

git.remoteRepo := "git@github.com:beamly/beamly.core.scalaz.git"

site.includeScaladoc()

autoAPIMappings := true

apiURL := Some(url("http://zeebox.github.io/beamly.core.scalaz/latest/api/"))

javacOptions in (Compile,doc) ++= Seq("-linksource")

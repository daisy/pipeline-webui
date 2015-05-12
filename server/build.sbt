name := """server"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayJava, PlayEbean, DebianPlugin)

scalaVersion := "2.11.6"

maintainer in Linux := "Jostein Austvik Jacobsen <josteinaj@gmail.com>"
packageSummary in Linux := "DAISY Pipeline 2 Web User Interface"
packageDescription := "DAISY Pipeline 2 Web User Interface"

resolvers += "Sonatype OSS Releases" at "https://oss.sonatype.org/content/repositories/releases/"
resolvers += "Local Maven Repository" at "file://"+Path.userHome.absolutePath+"/.m2/repository"

libraryDependencies ++= Seq(
  javaJdbc,
  cache,
  javaWs,
  
  "org.hibernate" % "hibernate-entitymanager" % "4.3.10.Final",
  "org.avaje.ebeanorm" % "avaje-ebeanorm-api" % "3.1.1",
  "org.apache.derby" % "derby" % "10.11.1.1",
  "org.daisy.pipeline" % "clientlib-java" % "3.0.0-SNAPSHOT",
  "org.daisy.pipeline" % "clientlib-java-httpclient" % "1.0.0-SNAPSHOT",
  "org.apache.commons" % "commons-compress" % "1.9",
  "org.apache.commons" % "commons-email" % "1.4",
  "log4j" % "log4j" % "1.2.17",
  "log4j" % "apache-log4j-extras" % "1.2.17"
)

scalacOptions += "-deprecation"

fork in run := true
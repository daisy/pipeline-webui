import com.typesafe.sbt.packager.archetypes.ServerLoader.{SystemV, Upstart}
import com.typesafe.sbt.packager.linux.LinuxSymlink

organization := "org.daisy.pipeline"
name := "webui"
version := "2.0-SNAPSHOT"

organizationName := "DAISY"
organizationHomepage := Some(url("http://daisy.org"))
homepage := Some(url("https://github.com/daisy/pipeline-webui"))
startYear := Some(2012)
description := "A web-based user interface for the DAISY Pipeline 2."
licenses += "LGPLv3" -> url("https://www.gnu.org/licenses/lgpl-3.0.html")

lazy val root = (project in file(".")).enablePlugins(PlayJava, PlayEbean, DebianPlugin)

scalaVersion := "2.11.6"
javacOptions ++= Seq("-source", "1.8", "-target", "1.8")

// For packaging on Linux
maintainer in Linux := "Jostein Austvik Jacobsen <josteinaj@gmail.com>"
packageName in Linux := "daisy-pipeline2-webui"
name in Linux := (packageName in Linux).value
packageSummary in Linux := "DAISY Pipeline 2 Web User Interface"
packageDescription := "A web-based user interface for the DAISY Pipeline 2."
daemonUser in Linux := "pipeline2-webui"
daemonGroup in Linux := (daemonUser in Linux).value
executableScriptName := "pipeline2-webui"
debianPackageDependencies in Debian += "java8-runtime"
debianPackageRecommends in Debian += "daisy-pipeline2"
serverLoading in Debian := SystemV
linuxPackageMappings += packageTemplateMapping(s"/usr/lib/"+(packageName in Linux).value)() withUser((daemonUser in Linux).value) withGroup((daemonGroup in Linux).value)
linuxPackageMappings += packageTemplateMapping(s"/var/run/"+(packageName in Linux).value)() withUser((daemonUser in Linux).value) withGroup((daemonGroup in Linux).value)
linuxPackageSymlinks += LinuxSymlink("/usr/share/"+(packageName in Linux).value+"/data", "/usr/lib/"+(packageName in Linux).value)
linuxPackageSymlinks += LinuxSymlink("/usr/lib/"+(packageName in Linux).value+"/logs", "/var/log/"+(packageName in Linux).value)
bashScriptExtraDefines += "export DP2DATA=\"$(realpath \"${app_home}/../data\")\" # storage for db, jobs, templates, uploads, etc."
bashScriptExtraDefines += "[[ ! -d \"$DP2DATA/db\" ]] && cp -r \"${app_home}/../db-empty\" \"$DP2DATA/db\" # create db if needed"
bashScriptExtraDefines += "addJava \"-Dpidfile.path=/var/run/"+(packageName in Linux).value+"/play.pid\""
bashScriptExtraDefines += "addJava \"-Ddb.default.url=jdbc:derby:$DP2DATA/db;create=true\""

resolvers += "Sonatype OSS Releases" at "https://oss.sonatype.org/content/repositories/releases/"
//resolvers += "Sonatype OSS Staging" at "https://oss.sonatype.org/content/repositories/staging/"
resolvers += "Local Maven Repository" at "file://"+Path.userHome.absolutePath+"/.m2/repository"

libraryDependencies ++= Seq(
  javaJdbc,
  cache,
  javaWs,
  
  "org.hibernate" % "hibernate-entitymanager" % "4.3.10.Final",
  "org.avaje.ebeanorm" % "avaje-ebeanorm-api" % "3.1.1",
  "org.apache.derby" % "derby" % "10.11.1.1",
  "org.daisy.pipeline" % "clientlib-java" % "4.3.0",
  "org.daisy.pipeline" % "clientlib-java-httpclient" % "1.1.0",
  "org.apache.commons" % "commons-compress" % "1.9",
  "org.apache.commons" % "commons-email" % "1.4",
  "log4j" % "log4j" % "1.2.17",
  "log4j" % "apache-log4j-extras" % "1.2.17"
)

scalacOptions += "-deprecation"

fork in run := false

// Compile the project before generating Eclipse files, so that generated .scala or .class files for views and routes are present
EclipseKeys.preTasks := Seq(compile in Compile)
// Java project. Don't expect Scala IDE
EclipseKeys.projectFlavor := EclipseProjectFlavor.Java
// Use .class files instead of generated .scala files for views and routes 
EclipseKeys.createSrc := EclipseCreateSrc.ValueSet(EclipseCreateSrc.ManagedClasses, EclipseCreateSrc.ManagedResources)

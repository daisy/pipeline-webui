import RpmConstants._
import com.typesafe.sbt.packager.rpm.RpmDependencies
import com.typesafe.sbt.packager.archetypes.ServerLoader.{SystemV, Upstart}
import com.typesafe.sbt.packager.linux.{LinuxPackageMapping, LinuxSymlink, LinuxFileMetaData}

organization := "org.daisy.pipeline"
name := "webui"
versionWithGit
git.useGitDescribe := true

organizationName := "DAISY"
organizationHomepage := Some(url("http://daisy.org"))
homepage := Some(url("https://github.com/daisy/pipeline-webui"))
startYear := Some(2012)
description := "A web-based user interface for the DAISY Pipeline 2."
maintainer := "Jostein Austvik Jacobsen <josteinaj@gmail.com>"
licenses += "LGPLv3" -> url("https://www.gnu.org/licenses/lgpl-3.0.html")

lazy val root = (project in file(".")).enablePlugins(PlayJava, PlayEbean, DebianPlugin, UniversalDeployPlugin, DebianDeployPlugin)

scalaVersion := "2.11.6"
javacOptions ++= Seq("-source", "1.8", "-target", "1.8")

// disable using the Scala version in output paths and artifacts
crossPaths := false

// Documentation for Debian packaging with sbt-native-packager available at:
// <http://www.scala-sbt.org/sbt-native-packager/formats/debian.html>
// For packaging on Linux (Debian flavor)
// Informational, dependency, meta, scriptlet, systemV start and script settings
packageName in Linux := "daisy-pipeline2-webui"
//name in Debian := (packageName in Linux).value
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
com.typesafe.sbt.packager.SettingsHelper.makeDeploymentSettings(Debian, packageBin in Debian, "deb")
com.typesafe.sbt.packager.SettingsHelper.makeDeploymentSettings(Universal, packageBin in Universal, "zip")
resolvers += Resolver.url("Typesafe Ivy releases", url("https://repo.typesafe.com/typesafe/ivy-releases"))(Resolver.ivyStylePatterns)
resolvers += "Sonatype OSS Releases" at "https://oss.sonatype.org/content/repositories/releases/"
//resolvers += "Sonatype OSS Staging" at "https://oss.sonatype.org/content/repositories/staging/"
resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
resolvers += "Local Maven Repository" at Path.userHome.asFile.toURI.toURL+".m2/repository/"

// Documentation for RPM packaging with sbt-native-packager available at:
// <http://www.scala-sbt.org/sbt-native-packager/formats/rpm.html>
// For packaging on Linux (CentOS/Redhat "RPM" flavor)
// Informational, dependency, meta, scriptlet, systemV start and script settings
packageName in Rpm := "pipeline2-webui"
version in Rpm := "1.8.1"
rpmRelease := Option(sys.props("rpm.buildNumber")) getOrElse "1"
daemonUser in Linux := "tpbadmin"
daemonGroup in Linux := (daemonUser in Linux).value
packageArchitecture in Rpm := "noarch"
packageSummary in Rpm := "DAISY Pipeline 2 Web User Interface."
packageDescription in Rpm := "A web-based user interface for the DAISY Pipeline 2."
rpmVendor := "The DAISY Consortium"
rpmUrl := Option("https://github.com/daisy/pipeline-webui")
rpmLicense := Option("LGPLv3")
executableScriptName := "pipeline2-webui"
rpmAutoreq += "java8-runtime"
rpmAutoreq += "pipeline2"
serverLoading in Linux := Upstart 
maintainerScripts in Rpm := Map(
  Pre -> Seq("""echo "pre-install""""),
  Post -> Seq("""rm -rf /opt/daisy-pipeline2-webui/webui/dp2webui && cp -rf /opt/daisy-pipeline2-webui/webui/db-empty /opt/daisy-pipeline2-webui/webui/dp2webui && rm -rf /opt/daisy-pipeline2-webui/webui/db-empty && service daisy-pipeline2-webui start"""),
  //Post -> Seq("""echo "post-install""""),
  Pretrans -> Seq("""echo "pretrans""""),
  Posttrans -> Seq("""echo "posttrans""""),
  Preun -> Seq("""rm -rf /opt/daisy-pipeline2-webui"""),
  Postun -> Seq("""echo "post-uninstall"""")
)
rpmBrpJavaRepackJars := false
bashScriptExtraDefines += "export DP2DATA=\"$(realpath \"${app_home}/../data\")\" # storage for db, jobs, templates, uploads, etc."
bashScriptExtraDefines += "[[ ! -d \"$DP2DATA/db\" ]] && cp -r \"${app_home}/../db-empty\" \"$DP2DATA/db\" # create db if needed"
bashScriptExtraDefines += "addJava \"-Dpidfile.path=/var/run/"+(packageName in Rpm).value+"/play.pid\""
//bashScriptExtraDefines += "addJava \"-Ddb.default.url=jdbc:derby:$DP2DATA/dp2webui;create=true\""
com.typesafe.sbt.packager.SettingsHelper.makeDeploymentSettings(Rpm, packageBin in Rpm, "rpm")
com.typesafe.sbt.packager.SettingsHelper.makeDeploymentSettings(Universal, packageBin in Universal, "zip")
resolvers += "Sonatype OSS Releases" at "https://oss.sonatype.org/content/repositories/releases/"
//resolvers += "Sonatype OSS Staging" at "https://oss.sonatype.org/content/repositories/staging/"
resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
resolvers += "Local Maven Repository" at Path.userHome.asFile.toURI.toURL+".m2/repository/"
resolvers += Resolver.url("Typesafe Ivy releases", url("https://repo.typesafe.com/typesafe/ivy-releases"))(Resolver.ivyStylePatterns)

publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (version.value.trim.endsWith("SNAPSHOT"))
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases"  at nexus + "service/local/staging/deploy/maven2")
}

publishMavenStyle := true
pomPostProcess := {
    import xml.transform._
    new RuleTransformer(new RewriteRule{
        override def transform(node:xml.Node) = {
            if (node.label == "packaging")
              <packaging>pom</packaging>
            else
              node
        }
    })
}
pomExtra := (
  <scm>
    <url>https://github.com/daisy/pipeline-webui</url>
    <connection>scm:git:git://github.com/daisy/pipeline-webui.git</connection>
  </scm>
  <developers>
    <developer>
      <id>josteinaj</id>
      <name>Jostein Austvik Jacobsen</name>
      <email>josteinaj@gmail.com</email>
      <organization>Norwegian Library of Talking Books and Braille</organization>
      <organizationUrl>http://www.nlb.no/</organizationUrl>
      <roles>
        <role>Developer</role>
      </roles>
      <timezone>UTC+01:00</timezone>
    </developer>
  </developers>
)

libraryDependencies ++= Seq(
  javaJdbc,
  cache,
  javaWs,
  
  "org.hibernate" % "hibernate-entitymanager" % "4.3.10.Final",
  "org.avaje.ebeanorm" % "avaje-ebeanorm-api" % "3.1.1",
  "org.apache.derby" % "derby" % "10.11.1.1",
  "org.daisy.pipeline" % "clientlib-java" % "4.4.1",
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
import sbt._
import Keys._
import PlayProject._

object ApplicationBuild extends Build {

    val appName         = "pipeline2-webui"
    val appVersion      = "1.0-SNAPSHOT"

    val appDependencies = Seq(
      // project dependencies
      "org.apache.derby" % "derby" % "10.9.1.0",
      "mysql" % "mysql-connector-java" % "5.1.18",
      "org.daisy.pipeline" % "pipeline2-clientlib" % "1.0-SNAPSHOT",
      "org.apache.commons" % "commons-compress" % "1.4.1",
      "org.apache.commons" % "commons-email" % "1.2"
    )

    val main = PlayProject(appName, appVersion, appDependencies, mainLang = JAVA).settings(
      // project settings
      ebeanEnabled := true,
      resolvers += ("Local Maven Repository" at "file://"+Path.userHome.absolutePath+"/.m2/repository"),
      resolvers += ("Public online Restlet repository" at "http://maven.restlet.org")
    )

}

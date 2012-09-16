import sbt._
import Keys._
import PlayProject._

object ApplicationBuild extends Build {

    val appName         = "pipeline2-webui"
    val appVersion      = "1.0-SNAPSHOT"

    val appDependencies = Seq(
      // project dependencies
      "mysql" % "mysql-connector-java" % "5.1.18",
      "org.daisy.pipeline" % "pipeline2-clientlib" % "1.0-SNAPSHOT"
    )

    val main = PlayProject(appName, appVersion, appDependencies, mainLang = JAVA).settings(
      // project settings
      resolvers += ("Local Maven Repository" at "file://"+Path.userHome.absolutePath+"/.m2/repository"),
      resolvers += ("Public online Restlet repository" at "http://maven.restlet.org")
    )

}

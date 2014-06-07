import sbt._
import Keys._
import play.Project._

object ApplicationBuild extends Build {

    def fromEnv(name: String) = System.getenv(name) match {
      case null => None
      case value => Some(value)
    }
    val appName = fromEnv("project.artifactId").getOrElse("webui")
    val appVersion = fromEnv("project.version").getOrElse("1.0-SNAPSHOT")

    val appDependencies = Seq(
      javaCore,
      javaJdbc,
      javaEbean,
      
      // project dependencies (remember to also update pom.xml!)
      "org.apache.derby" % "derby" % "10.9.1.0",
      "mysql" % "mysql-connector-java" % "5.1.18",
      "org.daisy.pipeline" % "clientlib-java" % "1.1.0",
      "org.apache.commons" % "commons-compress" % "1.4.1",
      "org.apache.commons" % "commons-email" % "1.2",
      "log4j" % "log4j" % "1.2.17",
      "log4j" % "apache-log4j-extras" % "1.1"
    )

    val main = play.Project(appName, appVersion, appDependencies).settings(
      // project settings
      ebeanEnabled := true,
      resolvers += ("Local Maven Repository" at "file:///"+Path.userHome.absolutePath+"/.m2/repository")
    ).settings(
      javacOptions ++= Seq("-source", "1.6", "-target", "1.6")
    )

}

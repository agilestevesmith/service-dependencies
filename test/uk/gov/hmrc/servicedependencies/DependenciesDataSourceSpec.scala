package uk.gov.hmrc.servicedependencies

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FreeSpec, Matchers, MustMatchers}

import scala.concurrent.Future

class DependenciesDataSourceSpec extends FreeSpec with Matchers with ScalaFutures {

  val servicesStub = new DeploymentsDataSource(new ReleasesConfig { override def releasesServiceUrl: String = ""}) {
    override def listOfRunningServices(): Future[List[Service]] = {
      Future.successful(List(
        Service("service1", Some("1.1.3"), Some("1.2.1"), Some("1.1.2")),
        Service("service2", Some("1.2.3"), Some("1.2.2"), Some("1.2.0")),
        Service("service3", Some("1.3.3"), Some("1.3.3"), None)
      ))
    }
  }

  class GithubStub(val map: Map[String, Option[String]]) extends Github("play-frontend", Seq()) {
    override val gh = null
    override def resolveTag(version: String) = version

    override def findArtifactVersion(serviceName: String, versionOption: Option[String]): Option[Version] = {
      versionOption match {
        case Some(version) =>
          map.get(s"$serviceName-$version").map(version =>
           version.map(Version.parse)).getOrElse(None)
        case None => None
      }
    }
  }

  val githubStub1 = new GithubStub(Map(
      "service1-1.1.3" -> Some("17.0.0"),
      "service1-1.2.1" -> Some("16.3.0"),
      "service1-1.1.2" -> Some("16.0.0")
    ))

  val githubStub2 = new GithubStub(Map(
      "service2-1.2.3" -> None,
      "service2-1.2.2" -> Some("15.0.0"),
      "service2-1.2.0" -> Some("15.0.0"),
      "service3-1.3.3" -> Some("16.3.0")
    ))

  "Pull together dependency artifact information for each environment and version" in {

    val dataSource = new DependenciesDataSource(servicesStub, Seq(githubStub1, githubStub2))
    val results = dataSource.getDependencies.futureValue

    results should contain(
      ServiceDependencies("service1", Map(
        "qa" -> EnvironmentDependency("1.1.3", "17.0.0"),
        "staging" -> EnvironmentDependency("1.2.1", "16.3.0"),
        "prod" -> EnvironmentDependency("1.1.2", "16.0.0"))))

    results should contain(
      ServiceDependencies("service2", Map(
        "qa" -> EnvironmentDependency("1.2.3", "N/A"),
        "staging" -> EnvironmentDependency("1.2.2", "15.0.0"),
        "prod" -> EnvironmentDependency("1.2.0", "15.0.0"))))

    results should contain(
      ServiceDependencies("service3", Map(
        "qa" -> EnvironmentDependency("1.3.3", "16.3.0"),
        "staging" -> EnvironmentDependency("1.3.3", "16.3.0"))))

  }
}
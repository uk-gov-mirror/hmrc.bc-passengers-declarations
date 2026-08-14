import sbt.*

object AppDependencies {

  private lazy val bootstrapPlayVersion = "10.8.0"
  private lazy val hmrcMongoVersion     = "2.13.0"

  private val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"                  %% "bootstrap-backend-play-30" % bootstrapPlayVersion,
    "com.networknt"                 % "json-schema-validator"     % "2.0.4" exclude ("com.fasterxml.jackson.core", "jackson-databind"),
    "org.mozilla"                   % "rhino"                     % "1.9.1",
    "uk.gov.hmrc.mongo"            %% "hmrc-mongo-play-30"        % hmrcMongoVersion,
    "com.fasterxml.jackson.module" %% "jackson-module-scala"      % "2.20.1"
  )

  private val test: Seq[ModuleID] = Seq(
    "org.scalacheck"    %% "scalacheck"              % "1.19.0",
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-test-play-30" % hmrcMongoVersion,
    "uk.gov.hmrc"       %% "bootstrap-test-play-30"  % bootstrapPlayVersion
  ).map(_ % Test)

  def apply(): Seq[ModuleID] = compile ++ test

}

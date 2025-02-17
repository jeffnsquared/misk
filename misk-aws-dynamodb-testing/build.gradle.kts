plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  api(Dependencies.awsDynamodb)
  api(Dependencies.guice)
  api(Dependencies.javaxInject)
  api(Dependencies.tempestTestingInternal)
  api(project(":misk-aws-dynamodb"))
  api(project(":misk-inject"))
  api(project(":misk-testing"))
  implementation(Dependencies.kotlinReflect)
  implementation(Dependencies.tempestTesting)
  implementation(Dependencies.tempestTestingDocker)
  implementation(Dependencies.tempestTestingJvm)
  implementation(project(":misk-core"))
  implementation(project(":misk-service"))

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.junitApi)
}

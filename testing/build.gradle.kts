import net.ltgt.gradle.errorprone.errorprone

plugins {
  id("org.curioswitch.curiostack.java-library")

  id("org.curioswitch.gradle-protobuf-plugin")
}

dependencies {
  api("com.google.protobuf:protobuf-java")
}

protobuf {
  protoc {
    artifact.set(
      "com.google.protobuf:protoc:${System.getenv(
        "PROTOBUF_VERSION",
      ).orEmpty().ifEmpty { "4.28.2" }}",
    )
  }

  descriptorSetOptions.enabled.set(false)
  descriptorSetOptions.path.set(file("build/unused-descriptor-set"))
}

tasks {
  withType<JavaCompile>().configureEach {
    with(options) {
      release.set(8)

      errorprone {
        excludedPaths.set(
          ".*com.google.protobuf.util.*|.*org.curioswitch.common.protobuf.json.test.*",
        )
      }

      // protoc generates code deprecated code so disable the lint.
      compilerArgs.add("-Xlint:-deprecation")
    }
  }

  spotlessJava {
    dependsOn(named("generateProto"))
  }

  spotlessJavaCheck {
    enabled = false
  }
}
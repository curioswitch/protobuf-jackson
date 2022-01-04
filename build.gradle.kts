import net.ltgt.gradle.errorprone.errorprone

plugins {
  jacoco

  id("org.curioswitch.curiostack.java-library")

  id("org.curioswitch.gradle-protobuf-plugin")
}

dependencies {
  api("com.fasterxml.jackson.core:jackson-core")
  api("com.google.protobuf:protobuf-java")

  implementation("net.bytebuddy:byte-buddy")

  // Used by byte-buddy but not brought in as a transitive dependency.
  compileOnly("com.google.code.findbugs:annotations")

  testImplementation("com.fasterxml.jackson.core:jackson-databind")
  testImplementation("com.google.protobuf:protobuf-java-util")
}

protobuf {
  protoc {
    artifact.set("com.google.protobuf:protoc:3.19.1")
  }

  descriptorSetOptions.enabled.set(false)
  descriptorSetOptions.path.set(file("build/unused-descriptor-set"))
}

tasks {
  withType<JavaCompile>().configureEach {
    with(options) {
      release.set(8)

      errorprone {
        excludedPaths.set(".*com.google.protobuf.util.*|.*org.curioswitch.common.protobuf.json.test.*")
      }
    }
  }
}

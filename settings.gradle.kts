pluginManagement {
  plugins {
    id("com.github.ben-manes.versions") version "0.40.0"
    id("org.curioswitch.gradle-protobuf-plugin") version "0.4.0"
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
  }
}

plugins {
  id("org.gradle.toolchains.foojay-resolver-convention")
}

dependencyResolutionManagement {
  repositories {
    mavenCentral()
    mavenLocal()
  }
}

include(":dependencyManagement")
include(":testing")
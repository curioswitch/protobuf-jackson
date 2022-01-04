pluginManagement {
  plugins {
    id("com.github.ben-manes.versions") version "0.40.0"
    id("org.curioswitch.gradle-protobuf-plugin") version "0.4.0"
  }
}

dependencyResolutionManagement {
  repositories {
    mavenCentral()
    mavenLocal()
  }
}

include(":dependencyManagement")

import nebula.plugin.release.git.opinion.Strategies
import net.ltgt.gradle.errorprone.errorprone

plugins {
  jacoco

  id("org.curioswitch.curiostack.java-library")
  id("org.curioswitch.curiostack.publishing")

  id("org.curioswitch.gradle-protobuf-plugin")

  id("io.github.gradle-nexus.publish-plugin")
  id("nebula.release")
}

release {
  defaultVersionStrategy = Strategies.getSNAPSHOT()
}

nebulaRelease {
  addReleaseBranchPattern("""v\d+\.\d+\.x""")
}

nexusPublishing {
  repositories {
    sonatype {
      username.set(System.getenv("MAVEN_USERNAME"))
      password.set(System.getenv("MAVEN_PASSWORD"))
    }
  }
}

description = "A library for efficient marshalling of Protocol Buffer messages to and from JSON."

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
  protoc { artifact.set("com.google.protobuf:protoc:3.22.3") }

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

      // protoc generates code deprecated code so disable the lint.
      if (name.contains("Test")) {
        compilerArgs.add("-Xlint:-deprecation")
      }
    }
  }

  named("release") {
    mustRunAfter("snapshotSetup", "finalSetup")
  }
}

publishing {
  publications {
    named<MavenPublication>("maven") {
      groupId = "org.curioswitch.curiostack"
      pom {
        name.set("protobuf-jackson")
        url.set("https://github.com/curioswitch/protobuf-jackson")

        licenses {
          license {
            name.set("MIT License")
            url.set("https://opensource.org/licenses/MIT")
          }
        }

        developers {
          developer {
            id.set("chokoswitch")
            name.set("Choko")
            email.set("choko@curioswitch.org")
            organization.set("CurioSwitch")
            organizationUrl.set("https://github.com/curioswitch/curiostack")
          }
        }

        scm {
          connection.set("scm:git:git://github.com/curioswitch/protobuf-jackson.git")
          developerConnection.set("scm:git:git://github.com/curioswitch/protobuf-jackson.git")
          url.set("git@github.com:curioswitch/protobuf-jackson.git")
        }
      }
    }
  }
}
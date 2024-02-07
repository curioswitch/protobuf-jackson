import nebula.plugin.release.git.opinion.Strategies

plugins {
  jacoco

  id("org.curioswitch.curiostack.java-library")
  id("org.curioswitch.curiostack.publishing")

  id("com.github.ben-manes.versions")
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
  compileOnly("com.fasterxml.jackson.core:jackson-databind")

  api("com.fasterxml.jackson.core:jackson-core")
  api("com.google.protobuf:protobuf-java")

  implementation("net.bytebuddy:byte-buddy")

  // Used by byte-buddy but not brought in as a transitive dependency.
  compileOnly("com.google.code.findbugs:annotations")

  testImplementation(project(":testing"))
  testImplementation("com.google.protobuf:protobuf-java-util")
}

testing {
  suites {
    register<JvmTestSuite>("testDatabind") {
      dependencies {
        implementation(project())
        implementation(project(":testing"))

        implementation("com.fasterxml.jackson.core:jackson-databind")
        implementation("com.google.protobuf:protobuf-java-util")
      }
    }
  }
}

tasks {
  withType<JavaCompile>().configureEach {
    with(options) {
      release.set(8)
    }
  }

  check {
    dependsOn(testing.suites.named("testDatabind"))
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
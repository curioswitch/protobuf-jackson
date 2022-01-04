plugins {
  `maven-publish`
  signing
}

publishing {
  publications {
    register<MavenPublication>("maven") {
      afterEvaluate {
        // Not available until evaluated.
        artifactId = base.archivesName.get()
        pom.description.set(project.description)
      }

      plugins.withId("java-platform") {
        from(components["javaPlatform"])
      }

      plugins.withId("java-library") {
        from(components["java"])
      }

      versionMapping {
        allVariants {
          fromResolutionResult()
        }
      }
    }
  }
}

// publish-plugin sets up the tasks in an afterEvaluate, so to guarantee sure ours is run afterwards,
// we wait for that plugin to finish.
rootProject.plugins.withId("io.github.gradle-nexus.publish-plugin") {
  project.afterEvaluate {
    val publishToSonatype by tasks.getting
    val release by rootProject.tasks.existing
    release.configure {
      finalizedBy(publishToSonatype)
    }
  }
}

if (System.getenv("CI") != null) {
  signing {
    useInMemoryPgpKeys(System.getenv("MAVEN_GPG_PRIVATE_KEY"), "")
    sign(publishing.publications["maven"])
  }
}

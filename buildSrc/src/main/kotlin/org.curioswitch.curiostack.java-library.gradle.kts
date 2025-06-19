import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
  `java-library`

  id("org.curioswitch.curiostack.errorprone")
  id("org.curioswitch.curiostack.spotless")
}

val testJavaVersion = gradle.startParameter.projectProperties.get("testJavaVersion")?.let(JavaVersion::toVersion)

group = "org.curioswitch.curiostack"

java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(21))
  }

  withJavadocJar()
  withSourcesJar()
}

val dependencyManagement by configurations.creating {
  isCanBeConsumed = false
  isCanBeResolved = false
  isVisible = false
}

configurations.configureEach {
  resolutionStrategy {
    failOnVersionConflict()
    preferProjectModules()
  }
}

dependencies {
  dependencyManagement(platform(project(":dependencyManagement")))
  afterEvaluate {
    configurations.configureEach {
      if (isCanBeResolved && !isCanBeConsumed) {
        extendsFrom(dependencyManagement)
      }
    }
  }

  compileOnly("com.google.code.findbugs:jsr305")
}

testing {
  suites.withType(JvmTestSuite::class).configureEach {
    dependencies {
      implementation("org.assertj:assertj-core")
      implementation("org.junit.jupiter:junit-jupiter-api")
      implementation("org.junit.jupiter:junit-jupiter-params")

      runtimeOnly("org.junit.jupiter:junit-jupiter-engine")
      runtimeOnly("org.junit.platform:junit-platform-launcher")
    }
  }
}

tasks {
  withType<JavaCompile>().configureEach {
    with(options) {
      compilerArgs.addAll(listOf(
        "-Xlint:all",
        "-Xlint:-options",
        "-Xlint:-serial",
        "-Werror"
      ))

      encoding = "UTF-8"
    }
  }

  withType<Test>().configureEach {
    useJUnitPlatform()

    if (testJavaVersion != null) {
      javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(testJavaVersion.majorVersion))
      })
    }

    testLogging {
      exceptionFormat = TestExceptionFormat.FULL
      showExceptions = true
      showCauses = true
      showStackTraces = true
    }
  }

  withType<Javadoc>().configureEach {
    with(options as StandardJavadocDocletOptions) {
      source = "8"
      encoding = "UTF-8"
      docEncoding = "UTF-8"
      breakIterator(true)

      addBooleanOption("html5", true)

      links("https://docs.oracle.com/javase/8/docs/api/")
      addBooleanOption("Xdoclint:all,-missing", true)
    }
  }
}

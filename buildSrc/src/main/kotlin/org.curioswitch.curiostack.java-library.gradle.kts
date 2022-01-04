import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
  `java-library`

  id("org.curioswitch.curiostack.errorprone")
  id("org.curioswitch.curiostack.spotless")
}

java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(17))
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
      implementation(project)

      implementation("org.assertj:assertj-core")
      implementation("org.junit.jupiter:junit-jupiter-api")
      implementation("org.junit.jupiter:junit-jupiter-params")

      runtimeOnly("org.junit.jupiter:junit-jupiter-engine")
    }
  }
}

tasks {
  withType<JavaCompile>().configureEach {
    with(options) {
      compilerArgs.addAll(listOf(
        "-Xlint:all",
        "-Werror"
      ))

      encoding = "UTF-8"
    }
  }

  withType<Test>().configureEach {
    useJUnitPlatform()

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

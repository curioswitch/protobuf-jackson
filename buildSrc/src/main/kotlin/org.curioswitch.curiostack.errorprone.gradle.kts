import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone
import net.ltgt.gradle.nullaway.nullaway

plugins {
  `java-library`

  id("net.ltgt.errorprone")
  id("net.ltgt.nullaway")
}

dependencies {
  errorprone("com.google.errorprone:error_prone_core")
  errorprone("com.uber.nullaway:nullaway")
}

tasks {
  withType<JavaCompile>().configureEach {
    with(options) {
      errorprone {
        disableWarningsInGeneratedCode.set(true)
        allDisabledChecksAsWarnings.set(true)

        disable("AndroidJdkLibsChecker")
        disable("Java7ApiChecker")

        disable("ImmutableMemberCollection")

        disable("Var")
        disable("Varifier")

        // TODO(chokoswitch): Consider suppressing only for the fields where avoiding
        // standard casing is more readable.
        disable("MemberName")

        // TODO(chokoswitch): Consider enabling
        disable("CanIgnoreReturnValueSuggester")
      }

      errorprone.nullaway {
        annotatedPackages.add("org.curioswitch")
        // Disable nullaway by default, we enable for main sources below.
        severity.set(CheckSeverity.OFF)
      }
    }
  }

  // Enable nullaway on main sources.
  compileJava {
    with(options) {
      errorprone.nullaway {
        severity.set(CheckSeverity.ERROR)
      }
    }
  }
}

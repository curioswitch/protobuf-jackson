plugins {
  `java-platform`
}

data class DependencySet(val group: String, val version: String, val modules: List<String>)

val dependencyVersions = hashMapOf<String, String>()
rootProject.extra["versions"] = dependencyVersions

val DEPENDENCY_BOMS = listOf(
  "com.fasterxml.jackson:jackson-bom:2.18.1",
  "com.google.guava:guava-bom:33.3.1-jre",
  "com.google.protobuf:protobuf-bom:${System.getenv("PROTOBUF_VERSION").orEmpty().ifEmpty { "4.28.2" }}",
  "org.junit:junit-bom:5.11.3",
)

val DEPENDENCY_SETS = listOf(
  DependencySet(
    "com.google.errorprone",
    "2.35.1",
    listOf("error_prone_annotations", "error_prone_core")
  )
)

val DEPENDENCIES = listOf(
  "com.google.code.findbugs:annotations:3.0.1",
  "com.google.code.findbugs:jsr305:3.0.2",
  "com.uber.nullaway:nullaway:0.12.1",
  "net.bytebuddy:byte-buddy:1.15.10",
  "org.assertj:assertj-core:3.26.3",
)

javaPlatform {
  allowDependencies()
}

dependencies {
  for (bom in DEPENDENCY_BOMS) {
    api(enforcedPlatform(bom))
    val split = bom.split(':')
    dependencyVersions[split[0]] = split[2]
  }
  constraints {
    for (set in DEPENDENCY_SETS) {
      for (module in set.modules) {
        api("${set.group}:$module:${set.version}")
        dependencyVersions[set.group] = set.version
      }
    }
    for (dependency in DEPENDENCIES) {
      api(dependency)
      val split = dependency.split(':')
      dependencyVersions[split[0]] = split[2]
    }
  }
}

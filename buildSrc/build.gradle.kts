plugins {
  `kotlin-dsl`
  // When updating, update below in dependencies too
  id("com.diffplug.spotless") version "6.1.0"
}

repositories {
  mavenCentral()
  gradlePluginPortal()
  mavenLocal()
}

dependencies {
  // When updating, update above in plugins too
  implementation("com.diffplug.spotless:spotless-plugin-gradle:7.0.4")
  implementation("com.netflix.nebula:nebula-release-plugin:20.2.0")
  implementation("io.github.gradle-nexus:publish-plugin:2.0.0")
  implementation("me.champeau.jmh:jmh-gradle-plugin:0.7.3")
  implementation("net.ltgt.gradle:gradle-errorprone-plugin:4.2.0")
  implementation("net.ltgt.gradle:gradle-nullaway-plugin:2.2.0")
}

spotless {
  kotlinGradle {
    ktlint().userData(mapOf("indent_size" to "2", "continuation_indent_size" to "2", "disabled_rules" to "no-wildcard-imports"))
  }
}

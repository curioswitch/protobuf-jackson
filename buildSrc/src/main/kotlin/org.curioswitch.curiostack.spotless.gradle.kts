plugins {
  id("com.diffplug.spotless")
}

val javaLicense =
  """
    /*
     * Copyright (c) ${'$'}YEAR Choko (choko@curioswitch.org)
     * SPDX-License-Identifier: MIT
     */


  """.trimIndent()

spotless {
  ratchetFrom("origin/main")

  java {
    googleJavaFormat()
    licenseHeader(javaLicense, "(package|import|public|// Includes work from:)")
  }
  kotlinGradle {
    ktlint().userData(mapOf("indent_size" to "2", "continuation_indent_size" to "2", "disabled_rules" to "no-wildcard-imports"))
  }
  format("misc") {
    // not using "**/..." to help keep spotless fast
    target(
      ".gitignore",
      ".gitattributes",
      ".gitconfig",
      ".editorconfig",
      "*.md",
      "src/**/*.md",
      "docs/**/*.md",
      "*.sh",
      "src/**/*.properties")
    indentWithSpaces()
    trimTrailingWhitespace()
    endWithNewline()
  }
}

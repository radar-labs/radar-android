plugins {
  id("org.jlleitschuh.gradle.ktlint")
}

configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
  version.set("1.5.0")
}

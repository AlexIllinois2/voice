plugins {
  id("voice.library")
  id("voice.compose")
  alias(libs.plugins.metro)
}

dependencies {
  implementation(projects.navigation)
  implementation(projects.core.common)
  implementation(projects.core.strings)
  implementation(projects.core.ui)
  implementation(projects.core.logging.api)
  implementation(projects.core.logging.inapp)

  implementation(libs.lifecycle)

  testImplementation(libs.bundles.testing.jvm)
  testImplementation(libs.molecule)
}

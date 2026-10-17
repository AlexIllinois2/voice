plugins {
  id("voice.library")
  alias(libs.plugins.metro)
}

kotlin {
  explicitApi()
}

dependencies {
  api(projects.core.logging.api)
  implementation(projects.core.initializer)
  implementation(libs.coroutines.core)

  testImplementation(libs.junit)
  testImplementation(libs.mockk)
  testImplementation(libs.coroutines.test)
}

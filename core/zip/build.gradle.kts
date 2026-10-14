plugins {
  id("voice.library")
  alias(libs.plugins.metro)
}

dependencies {
  api(projects.core.documentfile)
  api(projects.core.common)

  implementation(libs.androidxCore)
  implementation(libs.datastore)
  implementation(libs.media3.datasource)
  implementation(libs.serialization.json)
  implementation(libs.zip4j)
}

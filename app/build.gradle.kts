@file:Suppress("UnstableApiUsage")

import com.android.build.api.dsl.ManagedVirtualDevice
import java.util.Properties

plugins {
  id("voice.app")
  id("voice.compose")
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.metro)
}

android {

  namespace = "voice.app"

  dependenciesInfo {
    // disable the dependencies info in apks to allow reproducible builds
    // see https://github.com/PaulWoitaschek/Voice/discussions/2862#discussioncomment-13622836
    includeInApk = false
  }

  defaultConfig {
    applicationId = "de.ph1b.audiobook"
    versionName = providers.gradleProperty("voice.versionName").orNull ?: "1.0.0"
    versionCode = providers.gradleProperty("voice.versionCode").orNull?.toInt() ?: Int.MAX_VALUE

    testInstrumentationRunner = "voice.app.VoiceJUnitRunner"

    buildConfigField(type = "Boolean", name = "INCLUDE_ANALYTICS", value = "false")
    buildConfigField(type = "Boolean", name = "SUPPORT_DEVELOPMENT_INCLUDED", value = "true")

    // Only arm64-v8a is packaged by default; override via -Pvoice.abiFilters=arm64-v8a,x86_64
    // (needed for x86 emulators, which reject APKs without a matching ABI).
    ndk {
      abiFilters += providers.gradleProperty("voice.abiFilters")
        .orNull
        ?.split(',')
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?: listOf("arm64-v8a")
    }
  }

  val signingPropertiesFile = layout.projectDirectory.file("../signing/signing.properties").asFile
  val signingKeystoreFile = layout.projectDirectory.file("../signing/signing.keystore").asFile
  val appSigningConfig = if (signingPropertiesFile.isFile) {
    val signingProperties = Properties().apply {
      signingPropertiesFile.inputStream().use(::load)
    }
    signingConfigs.create("signing") {
      storeFile = signingKeystoreFile
      storePassword = signingProperties.getProperty("STORE_PASSWORD")
      keyAlias = signingProperties.getProperty("KEY_ALIAS")
      keyPassword = signingProperties.getProperty("KEY_PASSWORD")
    }
  } else {
    null
  }

  packaging {
    jniLibs {
      // :core:transcode ships a patched libavcodec.so (ffmpeg-kit) that declares a
      // libc++_shared.so dependency, which the original one is missing
      pickFirsts += listOf(
        "lib/arm64-v8a/libavcodec.so",
        "lib/armeabi-v7a/libavcodec.so",
        "lib/x86/libavcodec.so",
        "lib/x86_64/libavcodec.so",
      )
    }
  }

  buildTypes {
    getByName("release") {
      isMinifyEnabled = true
      isShrinkResources = true
    }
    getByName("debug") {
      isMinifyEnabled = false
    }
    all {
      if (appSigningConfig != null) {
        signingConfig = appSigningConfig
      }
      setProguardFiles(
        listOf(
          getDefaultProguardFile("proguard-android-optimize.txt"),
          "proguard.pro",
        ),
      )
    }
  }

  testOptions {
    unitTests {
      isReturnDefaultValues = true
      isIncludeAndroidResources = true
    }
    animationsDisabled = true
    execution = "ANDROIDX_TEST_ORCHESTRATOR"
    managedDevices {
      allDevices.create("voiceDevice", ManagedVirtualDevice::class.java) {
        device = "Pixel 9"
        apiLevel = 33
      }
    }
  }

  lint {
    checkDependencies = true
    ignoreTestSources = true
    checkReleaseBuilds = false
    // arm64-v8a only packaging is a deliberate distribution choice.
    disable += "ChromeOsAbiSupport"
    // The playback wake lock is held for the whole playback duration and released explicitly.
    disable += "WakelockTimeout"
    warningsAsErrors = providers.gradleProperty("voice.warningsAsErrors").get().toBooleanStrict()
  }

  packaging {
    // the bundled ffmpeg executable is executed from nativeLibraryDir, which requires the
    // native libraries to be extracted at install time
    jniLibs.useLegacyPackaging = true
    with(resources.pickFirsts) {
      add("META-INF/atomicfu.kotlin_module")
      add("META-INF/core.kotlin_module")
    }
  }

  buildFeatures {
    buildConfig = true
  }
}

// Debug-signed release APKs get a fixed file name so CI and local builds produce the same artifact name.
androidComponents {
  onVariants(selector().withBuildType("release")) { variant ->
    variant.outputs.forEach { output ->
      output.outputFileName.set("voice-release-debug-signed.apk")
    }
  }
}

dependencies {
  implementation(projects.core.strings)
  implementation(projects.core.ui)
  implementation(projects.core.common)
  implementation(projects.core.data.api)
  implementation(projects.core.data.impl)
  implementation(projects.core.playback)
  implementation(projects.core.scanner)
  implementation(projects.core.zip)
  implementation(projects.core.transcode)
  implementation(projects.core.featureflag)
  implementation(projects.core.initializer)
  implementation(projects.features.playbackScreen)
  implementation(projects.navigation)
  implementation(projects.core.sleeptimer.api)
  implementation(projects.core.sleeptimer.impl)
  implementation(projects.features.sleepTimer)
  implementation(projects.features.settings)
  implementation(projects.features.folderPicker)
  implementation(projects.features.logViewer)
  implementation(projects.features.bookOverview)
  implementation(projects.core.search)
  implementation(projects.features.cover)
  implementation(projects.core.documentfile)
  implementation(projects.features.onboarding)
  implementation(projects.features.bookmark)
  implementation(projects.features.widget)

  implementation(libs.appCompat)
  implementation(libs.lifecycle.compose)
  implementation(libs.datastore)

  implementation(libs.navigation3.ui)

  implementation(libs.serialization.json)

  implementation(libs.coil)

  implementation(projects.features.review.noop)
  implementation(projects.features.support.free)

  implementation(projects.core.remoteconfig.api)
  implementation(projects.core.remoteconfig.noop)

  implementation(projects.core.analytics.api)
  implementation(projects.core.analytics.noop)

  // the in-app log writer keeps the parse errors in memory so that the user can inspect them
  implementation(projects.core.logging.inapp)

  debugImplementation(projects.core.logging.debug)

  implementation(libs.androidxCore)

  testImplementation(libs.junit)
  testImplementation(libs.mockk)

  implementation(libs.media3.session)

  testImplementation(libs.androidX.test.runner)
  testImplementation(libs.androidX.test.junit)
  testImplementation(libs.androidX.test.core)
  testImplementation(libs.robolectric)
  testImplementation(libs.coroutines.test)
  testImplementation(kotlin("reflect"))

  debugImplementation(libs.compose.ui.testManifest)

  androidTestImplementation(platform(libs.compose.bom))
  androidTestImplementation(libs.androidX.test.runner)
  androidTestImplementation(libs.androidX.test.rules)
  androidTestImplementation(libs.androidX.test.junit)
  androidTestImplementation(libs.media3.testUtils.core)
  androidTestImplementation(libs.kotlin.testJunit)
  androidTestImplementation(libs.androidX.test.services)
  androidTestImplementation(libs.compose.ui.testJunit)
  androidTestImplementation(libs.coroutines.test)
  androidTestUtil(libs.androidX.test.orchestrator)
}

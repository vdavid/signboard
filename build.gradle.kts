plugins {
  // AGP 9 has built-in Kotlin support; a separate org.jetbrains.kotlin.android plugin
  // is no longer required and AGP rejects it outright.
  id("com.android.application") version "9.3.0"
  id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
}

android {
  namespace = "com.veszelovszki.signboard"
  compileSdk = 35
  lint {
    // Lint the release build too, and treat findings as build failures. This app is small
    // enough that a clean lint run is achievable, so anything new should be dealt with
    // rather than accumulated. Prefer fixing over adding to the disable list below.
    checkReleaseBuilds = true
    abortOnError = true
    warningsAsErrors = true
    checkDependencies = true

    disable += listOf(
      // The launcher icon fills its square deliberately. Google Play's icon spec asks for
      // full-bleed artwork and applies its own 30% corner mask and shadow, so the shape this
      // check wants would be masked twice. See branding/CLAUDE.md.
      "IconLauncherShape",
      // compileSdk/targetSdk are held at 35 on purpose. Raising targetSdk opts into new
      // runtime restrictions and needs testing on a real device, which is a separate job
      // from shipping this build. Revisit deliberately, not by silencing.
      "OldTargetApi",
      "GradleDependency",
    )
  }

  // The release key lives outside the repo and cannot be regenerated. Builds without it (CI,
  // a fresh clone) still produce a release APK, just an unsigned one, rather than failing: that
  // keeps CI able to verify minification, lint, and size without holding the signing key.
  val releaseKeystore = file(System.getProperty("user.home") + "/.android/signboard-release.keystore")

  signingConfigs {
    if (releaseKeystore.exists()) {
      create("release") {
        storeFile = releaseKeystore
        storePassword = System.getenv("SIGNBOARD_KEYSTORE_PASSWORD") ?: "signboard-release-key"
        keyAlias = "signboard"
        keyPassword = System.getenv("SIGNBOARD_KEY_PASSWORD") ?: "signboard-release-key"
      }
    }
  }

  defaultConfig {
    applicationId = "com.veszelovszki.signboard"
    minSdk = 26
    targetSdk = 35
    versionCode = 5
    versionName = "1.2"
  }

  buildTypes {
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      signingConfig = signingConfigs.findByName("release")
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }

  packaging {
    resources {
      // Kotlin builtins/metadata describe the stdlib for kotlin-reflect and IDE tooling.
      // Nothing reads them at runtime unless the app uses reflection, and this one doesn't,
      // so they're ~30 KB of the packaged size for nothing. If reflection is ever added
      // (kotlin-reflect, or a library that reflects over Kotlin types), drop these excludes.
      excludes +=
        listOf(
          "kotlin/**",
          "kotlin-tooling-metadata.json",
          "DebugProbesKt.bin",
          "META-INF/*.version",
          "META-INF/**.kotlin_module",
        )
    }
  }

  // Keeps the SDK dependency blob out of the shipped artifacts. It's Play-only metadata.
  dependenciesInfo {
    includeInApk = false
    includeInBundle = false
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
}

// Compile on JDK 21 regardless of the JDK the Gradle daemon happens to launch on.
// Homebrew's Gradle currently runs on JDK 26, and the IntelliJ utility code embedded in the
// Kotlin compiler can't parse a "26.0.1" version string: it throws deep inside the
// incremental-compilation cache, which surfaces only as a stray "Daemon compilation failed:
// null" while the build still reports SUCCESS. Pinning the toolchain avoids the whole class
// of problem and makes builds reproducible across machines and CI.
kotlin {
  jvmToolchain(21)
  compilerOptions {
    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
  }
}

// No dependencies, deliberately. The app needs an Activity, a TextView, a dialog, and a
// JSON array, and the Android framework provides all four. Adding androidx.appcompat back
// costs ~400 KB (it drags in ~280 resource entries and most of resources.arsc) to supply an
// Activity base class this app doesn't use a single feature of; androidx.core cost ~90 KB
// for one WindowInsets accessor. See src/main/kotlin/.../MainActivity.kt for the framework
// equivalents. Adding any dependency here should be weighed against those numbers.
dependencies {
}

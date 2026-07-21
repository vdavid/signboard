plugins {
  id("com.android.application") version "8.6.0"
  kotlin("android") version "2.0.0"
}

android {
  namespace = "com.veszelovszki.signboard"
  compileSdk = 35
  lint {
    disable += listOf("MissingTranslation", "ExtraTranslation")
    checkReleaseBuilds = false
  }

  signingConfigs {
    create("release") {
      storeFile = file(System.getProperty("user.home") + "/.android/signboard-release.keystore")
      storePassword = "signboard-release-key"
      keyAlias = "signboard"
      keyPassword = "signboard-release-key"
    }
  }

  defaultConfig {
    applicationId = "com.veszelovszki.signboard"
    minSdk = 26
    targetSdk = 35
    versionCode = 3
    versionName = "1.1"
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      isShrinkResources = false
      signingConfig = signingConfigs.getByName("release")
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }

  kotlinOptions {
    jvmTarget = "11"
  }
}

dependencies {
  // Only the essentials from Android framework
  implementation("androidx.appcompat:appcompat:1.7.0")
  implementation("androidx.core:core:1.13.1")
  // JSON for storing history
  implementation("org.json:json:20240303")
}

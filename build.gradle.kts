plugins {
  id("com.android.application") version "8.6.0"
  kotlin("android") version "2.0.0"
}

android {
  namespace = "com.example.signboard"
  compileSdk = 35
  lint {
    disable.add("MissingTranslation")
    disable.add("ExtraTranslation")
  }

  defaultConfig {
    applicationId = "com.example.signboard"
    minSdk = 26
    targetSdk = 35
    versionCode = 1
    versionName = "1.0"
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      isShrinkResources = false
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

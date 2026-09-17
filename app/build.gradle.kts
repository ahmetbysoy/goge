plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.kalkankeyboard.core"
    minSdk = 24
    targetSdk = 36
    versionCode = 2
    versionName = "1.1"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    // Drop emulator ABIs from release APK (phones are arm*).
    ndk {
      abiFilters += listOf("armeabi-v7a", "arm64-v8a")
    }
  }

  signingConfigs {
    // Release signing is optional: only applied when KEYSTORE_PATH + passwords are provided (CI secrets).
    val keystorePath = System.getenv("KEYSTORE_PATH")
    val storePasswordEnv = System.getenv("STORE_PASSWORD")
    val keyPasswordEnv = System.getenv("KEY_PASSWORD")
    val keyAliasEnv = System.getenv("KEY_ALIAS") ?: "upload"
    if (!keystorePath.isNullOrBlank() &&
        !storePasswordEnv.isNullOrBlank() &&
        !keyPasswordEnv.isNullOrBlank() &&
        file(keystorePath).exists()) {
      create("release") {
        storeFile = file(keystorePath)
        storePassword = storePasswordEnv
        keyAlias = keyAliasEnv
        keyPassword = keyPasswordEnv
      }
    }

    create("debugConfig") {
      val projectDebug = file("${rootDir}/debug.keystore")
      val homeDebug = file("${System.getProperty("user.home")}/.android/debug.keystore")
      storeFile =
        when {
          projectDebug.exists() -> projectDebug
          homeDebug.exists() -> homeDebug
          else -> projectDebug // CI generates this path before the build
        }
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = true
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig =
        signingConfigs.findByName("release") ?: signingConfigs.getByName("debugConfig")
    }
    // Also minify debug so CI "debug" artifact stays lean for sideload testing.
    debug {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("debugConfig")
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }

  buildFeatures {
    compose = true
    buildConfig = false
  }

  packaging {
    resources {
      excludes +=
        setOf(
          "META-INF/LICENSE*",
          "META-INF/NOTICE*",
          "META-INF/*.kotlin_module",
          "META-INF/AL2.0",
          "META-INF/LGPL2.1",
          "kotlin/**",
          "DebugProbesKt.bin",
        )
    }
    jniLibs {
      // Keep only device ABIs (matches ndk.abiFilters)
      excludes += setOf("**/x86/**", "**/x86_64/**")
    }
  }

  testOptions { unitTests { isIncludeAndroidResources = true } }

  dependenciesInfo {
    includeInApk = false
    includeInBundle = false
  }
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)

  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.material3)
  // Core icons only — material-icons-extended alone can add several MB.
  implementation(libs.androidx.compose.material.icons.core)

  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)

  // Cloud sync uses OkHttp only (no Retrofit / Moshi / Firebase).
  implementation(libs.okhttp)

  // Tests (not packaged into APK)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  androidTestImplementation(composeBom)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.tooling)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling.preview)
}

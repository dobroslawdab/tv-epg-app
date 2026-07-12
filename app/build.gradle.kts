import java.util.Properties

plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
  id("org.jetbrains.kotlin.plugin.compose")
  id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21"
  id("com.google.devtools.ksp") version "2.0.21-1.0.27"
}

android {
  namespace = "com.uxellence.tv.v3"
  compileSdk = 36

  signingConfigs {
      create("release") {
          val properties = Properties()
          val localPropertiesFile = rootProject.file("local.properties")
          if (localPropertiesFile.exists()) {
              properties.load(localPropertiesFile.inputStream())
          }
          storeFile = file(properties.getProperty("RELEASE_STORE_FILE", "${System.getProperty("user.home")}/test_makieta_tv.jks"))
          storePassword = properties.getProperty("RELEASE_STORE_PASSWORD", "")
          keyAlias = properties.getProperty("RELEASE_KEY_ALIAS", "")
          keyPassword = properties.getProperty("RELEASE_KEY_PASSWORD", "")
      }
  }

      defaultConfig {
          applicationId = "com.uxellence.tv.prod"
          minSdk = 23  // Compose 1.10.0 requires minSdk 23
          targetSdk = 36
          versionCode = 66
          versionName = "5.14.0"
  
          // Specify the ABIs to build for. Including both 32-bit and 64-bit ensures compatibility.
          ndk {
              abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a"))
          }
  
          vectorDrawables { useSupportLibrary = true }
    // Load API keys from local.properties
    val properties = Properties()
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
      properties.load(localPropertiesFile.inputStream())
    }

    buildConfigField("String", "GEMINI_API_KEY", "\"${properties.getProperty("GEMINI_API_KEY", "")}\"")
    buildConfigField("String", "TMDB_API_KEY", "\"${properties.getProperty("TMDB_API_KEY", "716cc02044e4d92d0a012a426902cc2d")}\"")
    buildConfigField("String", "ELEVENLABS_API_KEY", "\"${properties.getProperty("ELEVENLABS_API_KEY", "")}\"")
  }

  buildTypes {
    release {
      signingConfig = signingConfigs.getByName("release")
      isMinifyEnabled = false
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro"
      )
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    isCoreLibraryDesugaringEnabled = true
  }
  kotlinOptions { jvmTarget = "17" }

  buildFeatures {
    compose = true
    buildConfig = true
  }
  // composeOptions not needed with Kotlin 2.0+ (compose plugin manages compiler)

  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
  }

  // Włącz HW accel (powinno być domyślne, ale jawnie dodajemy)
  buildFeatures.apply {}
}

dependencies {
  // Compose BOM to align versions (2025.12.00 for dropShadow with spread = outside outline)
  implementation(platform("androidx.compose:compose-bom:2025.12.00"))

  implementation("androidx.activity:activity-compose:1.9.0")
  implementation("androidx.compose.ui:ui")
  implementation("androidx.compose.material3:material3")
  implementation("androidx.compose.material:material-icons-core")
  implementation("androidx.compose.material:material-icons-extended")
  implementation("androidx.compose.ui:ui-tooling-preview")
  debugImplementation("androidx.compose.ui:ui-tooling")

  // Android TV Compose (opcjonalne) — usunięte na razie, bo brak stabilnej wersji; nieużywane w kodzie
  // implementation("androidx.tv:tv-foundation:<version>")
  // implementation("androidx.tv:tv-material:<version>")

  // Rive removed (project limited to EPG only)

  // Coil for image loading (channel logos) and GIF support
  implementation("io.coil-kt:coil-compose:2.7.0")
  implementation("io.coil-kt:coil-gif:2.7.0")
  
  // Lottie for animations
  implementation("com.airbnb.android:lottie-compose:6.2.0")
  
  // ExoPlayer for mini-player
  implementation("com.google.android.exoplayer:exoplayer:2.19.1")
  implementation("com.google.android.exoplayer:exoplayer-hls:2.19.1")
  
  // Room Database for EPG cache
  implementation("androidx.room:room-runtime:2.6.1")
  implementation("androidx.room:room-ktx:2.6.1")
  ksp("androidx.room:room-compiler:2.6.1")
  
  // Kotlinx Serialization for JSON parsing
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

  // HTTP Client for Gemini API
  implementation("io.ktor:ktor-client-core:2.3.7")
  implementation("io.ktor:ktor-client-android:2.3.7")
  implementation("io.ktor:ktor-client-content-negotiation:2.3.7")
  implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.7")
  implementation("io.ktor:ktor-client-logging:2.3.7")

  // ElevenLabs Speech-to-Text API
  implementation("com.squareup.okhttp3:okhttp:4.12.0")
  implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
  implementation("com.squareup.retrofit2:retrofit:2.11.0")
  implementation("com.squareup.retrofit2:converter-gson:2.11.0")
  implementation("com.google.code.gson:gson:2.10.1")

  // ViewModel for state management
  implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
  implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")

  // Core library desugaring for Java 8+ API support (NIO version needed for NewPipe Extractor)
  coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.0.4")

  // Haze - blur background library by Chris Banes
  implementation("dev.chrisbanes.haze:haze:1.0.0")

  // NewPipe Extractor removed - using Vercel API with yt-dlp instead
}


plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
  id("org.jetbrains.kotlin.plugin.serialization") version "1.9.24"
  id("com.google.devtools.ksp") version "1.9.24-1.0.20"
}

android {
  namespace = "com.example.tv"
  compileSdk = 34

  defaultConfig {
    applicationId = "com.example.tv"
    minSdk = 23
    targetSdk = 34
    versionCode = 1
    versionName = "1.0"

    vectorDrawables { useSupportLibrary = true }
  }

  buildTypes {
    release {
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

  buildFeatures { compose = true }
  composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }

  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
  }

  // Włącz HW accel (powinno być domyślne, ale jawnie dodajemy)
  buildFeatures.apply {}
}

dependencies {
  // Compose BOM to align versions
  implementation(platform("androidx.compose:compose-bom:2024.06.00"))

  implementation("androidx.activity:activity-compose:1.9.0")
  implementation("androidx.compose.ui:ui")
  implementation("androidx.compose.material3:material3")
  implementation("androidx.compose.ui:ui-tooling-preview")
  debugImplementation("androidx.compose.ui:ui-tooling")

  // Android TV Compose (opcjonalne) — usunięte na razie, bo brak stabilnej wersji; nieużywane w kodzie
  // implementation("androidx.tv:tv-foundation:<version>")
  // implementation("androidx.tv:tv-material:<version>")

  // Rive removed (project limited to EPG only)

  // Coil for image loading (channel logos)
  implementation("io.coil-kt:coil-compose:2.7.0")
  
  // ExoPlayer for mini-player
  implementation("com.google.android.exoplayer:exoplayer:2.19.1")
  implementation("com.google.android.exoplayer:exoplayer-hls:2.19.1")
  
  // Room Database for EPG cache
  implementation("androidx.room:room-runtime:2.6.1")
  implementation("androidx.room:room-ktx:2.6.1")
  ksp("androidx.room:room-compiler:2.6.1")
  
  // Kotlinx Serialization for JSON parsing
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
  
  // Core library desugaring for Java 8 Time API support on older Android versions
  coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
}


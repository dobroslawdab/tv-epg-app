plugins {
  id("com.android.application") version "8.13.2" apply false
  id("org.jetbrains.kotlin.android") version "2.0.21" apply false
  id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}

// Root build file kept minimal; Android Studio can upgrade versions if needed.
// Kotlin 2.0.21 required for Compose BOM 2025.08.00+ (dropShadow, innerShadow API)





plugins {
    id("com.android.application") version "9.0.1" apply false
    // AGP 9 compiles Kotlin itself (built-in Kotlin), so only the Compose compiler plugin is applied
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
}

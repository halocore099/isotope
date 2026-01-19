pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.kikugie.dev/releases")
        maven("https://maven.fabricmc.net/")
        maven("https://maven.architectury.dev/")
        maven("https://maven.neoforged.net/releases/")
        maven("https://maven.minecraftforge.net/")
    }
}

// Note: We're not using Stonecutter's preprocessing because it doesn't support
// Architectury's multi-module structure. Instead, we use Gradle-based version
// selection and runtime reflection for cross-version compatibility.
//
// To build for a different version, change the active version in stonecutter.gradle.kts
// and rebuild. The active version determines which gradle.properties to load from versions/.

rootProject.name = "isotope"

include("common")
include("fabric")
include("neoforge")

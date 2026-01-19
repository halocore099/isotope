pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/")
        maven("https://maven.architectury.dev/")
        maven("https://maven.neoforged.net/releases")
        maven("https://maven.kikugie.dev/releases")
        gradlePluginPortal()
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.5.1"
}

stonecutter {
    kotlinController = true
    centralScript = "build.gradle"

    // Use shared mode for multi-module projects
    // 3 version groups, each JAR covers a range:
    // - 1.20.1: Only 1.20.1 (different mixin target: LootDataManager)
    // - 1.20.4: Covers 1.20.2-1.20.6 (mixin: ReloadableServerRegistries.Holder, old registry API)
    // - 1.21.4: Covers 1.21-1.21.4 (mixin: ReloadableServerRegistries.Holder, new registry API)
    shared {
        versions("1.20.1", "1.20.4", "1.21.4")
        vcsVersion = "1.21.4"
    }
}

rootProject.name = "isotope"

include("common")
include("fabric")
include("neoforge")
include("forge")

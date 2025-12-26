pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "SpaceTec"

// Simplified SpaceTec - Core functionality only
include(":app")
include(":core:common")
include(":core:domain")
include(":features:dtc")

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

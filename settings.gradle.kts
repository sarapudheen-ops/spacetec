pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
        maven { url = uri("https://s01.oss.sonatype.org/content/groups/public/") }
    }
}

rootProject.name = "SpaceTec"

// ============================================================================
// APPLICATION MODULE
// ============================================================================
include(":app")

// ============================================================================
// CORE MODULES - Foundation layer providing shared utilities and infrastructure
// ============================================================================
include(":core:common")
// include(":core:database") // Commented out due to KSP errors
include(":core:network")
include(":core:datastore")
include(":core:security")
include(":core:logging")
include(":core:domain") // Enabled - required by core:ui and features
// include(":core:data") // Disabled - depends on database
include(":core:ui")
include(":core:testing")

// ============================================================================
// PROTOCOL MODULES - OBD-II and vehicle communication protocols
// ============================================================================
include(":protocol:core")
include(":protocol:obd")
include(":protocol:uds")
include(":protocol:can")
include(":protocol:kline")
include(":protocol:j1850")
include(":protocol:safety")
include(":protocol:security")

// ============================================================================
// SCANNER MODULES - Scanner connectivity implementations
// ============================================================================
include(":scanner:core")
include(":scanner:bluetooth")
include(":scanner:wifi")
include(":scanner:usb")
include(":scanner:j2534")
include(":scanner:devices")

// ============================================================================
// FEATURE MODULES - User-facing feature implementations
// ============================================================================
include(":features:dtc")
include(":features:livedata")
include(":features:freezeframe")
include(":features:reports")
// include(":features:coding")  // Disabled due to protocol dependency
// include(":features:bidirectional")  // Disabled due to protocol dependency
// include(":features:keyprogramming")  // Disabled due to protocol dependency
include(":features:ecu")
include(":features:maintenance")
include(":features:dashboard")
include(":features:settings")
include(":features:connection")
include(":features:vehicle")

// ============================================================================
// VEHICLE BRAND MODULES - Manufacturer-specific implementations
// ============================================================================
include(":vehicle:core")
include(":vehicle:brands:audi")
include(":vehicle:brands:bmw")
include(":vehicle:brands:mercedes")
include(":vehicle:brands:volkswagen")
include(":vehicle:brands:porsche")
include(":vehicle:brands:toyota")
include(":vehicle:brands:lexus")
include(":vehicle:brands:honda")
include(":vehicle:brands:nissan")
include(":vehicle:brands:mazda")
include(":vehicle:brands:subaru")
include(":vehicle:brands:ford")
include(":vehicle:brands:chevrolet")
include(":vehicle:brands:gm")
include(":vehicle:brands:chrysler")
include(":vehicle:brands:dodge")
include(":vehicle:brands:jeep")
include(":vehicle:brands:hyundai")
include(":vehicle:brands:kia")
include(":vehicle:brands:volvo")
include(":vehicle:brands:jaguar")
include(":vehicle:brands:landrover")
include(":vehicle:brands:generic")

// ============================================================================
// ANALYSIS MODULES - Intelligent diagnostic analysis
// ============================================================================
include(":analysis:core")
include(":analysis:ml")
include(":analysis:patterns")
include(":analysis:predictions")

// ============================================================================
// COMPLIANCE MODULES - Regulatory compliance implementations
// ============================================================================
include(":compliance:core")
include(":compliance:carb")
include(":compliance:euro6")
include(":compliance:china6")
include(":compliance:india-bs6")
include(":compliance:emissions")

// ============================================================================
// TRANSPORT CONTRACT MODULE - Shared interfaces to break scanner/protocol circular dependency
// ============================================================================
include(":transport:contract")

// ============================================================================
// Enable type-safe project accessors
// ============================================================================
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
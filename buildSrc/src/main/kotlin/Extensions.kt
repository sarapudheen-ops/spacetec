import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

/**
 * Utility extension functions for Gradle build scripts
 */

/**
 * Access the version catalog from any project
 */
internal val Project.libs
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

/**
 * Configure common Android settings
 */
internal fun CommonExtension<*, *, *, *, *>.configureAndroidCommon() {
    compileSdk = SpaceTecBuildConfig.COMPILE_SDK
    
    defaultConfig {
        minSdk = SpaceTecBuildConfig.MIN_SDK
        
        vectorDrawables {
            useSupportLibrary = true
        }
    }
    
    compileOptions {
        sourceCompatibility = SpaceTecBuildConfig.JAVA_VERSION
        targetCompatibility = SpaceTecBuildConfig.JAVA_VERSION
        isCoreLibraryDesugaringEnabled = true
    }
    
    packaging {
        resources {
            excludes += SpaceTecBuildConfig.PackagingOptions.EXCLUDED_RESOURCES
        }
    }
}

/**
 * Configure Compose for Android modules
 */
internal fun CommonExtension<*, *, *, *, *>.configureCompose() {
    buildFeatures {
        compose = true
    }
}

/**
 * Get a library from the version catalog
 */
internal fun Project.library(alias: String) =
    libs.findLibrary(alias).get()

/**
 * Get a version from the version catalog
 */
internal fun Project.version(alias: String) =
    libs.findVersion(alias).get().toString()

/**
 * Get a plugin from the version catalog
 */
internal fun Project.plugin(alias: String) =
    libs.findPlugin(alias).get().get().pluginId

/**
 * Check if this is a feature module
 */
internal val Project.isFeatureModule: Boolean
    get() = path.startsWith(":features:")

/**
 * Check if this is a core module
 */
internal val Project.isCoreModule: Boolean
    get() = path.startsWith(":core:")

/**
 * Check if this is a vehicle brand module
 */
internal val Project.isVehicleBrandModule: Boolean
    get() = path.startsWith(":vehicle:brands:")

/**
 * Check if this is a protocol module
 */
internal val Project.isProtocolModule: Boolean
    get() = path.startsWith(":protocol:")

/**
 * Check if this is a scanner module
 */
internal val Project.isScannerModule: Boolean
    get() = path.startsWith(":scanner:")

/**
 * Convert module path to namespace
 * Example: ":core:common" -> "com.spacetec.automotive.core.common"
 */
internal fun Project.pathToNamespace(): String {
    return SpaceTecBuildConfig.APPLICATION_ID + path
        .removePrefix(":")
        .replace(":", ".")
        .replace("-", "")
}
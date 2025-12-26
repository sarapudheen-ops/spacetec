/**
 * SpaceTec Automotive DTC System - Build Configuration
 * 
 * Centralizes all build configuration constants and settings
 * for the multi-module Android project.
 */
object SpaceTecBuildConfig {
    
    // ========================================================================
    // APPLICATION IDENTIFIERS
    // ========================================================================
    const val APPLICATION_ID = "com.spacetec.obd"
    const val APPLICATION_ID_DEBUG = "$APPLICATION_ID.debug"
    const val APPLICATION_ID_STAGING = "$APPLICATION_ID.staging"
    
    // ========================================================================
    // SDK VERSIONS
    // ========================================================================
    const val MIN_SDK = 26
    const val TARGET_SDK = 34
    const val COMPILE_SDK = 34
    
    // ========================================================================
    // VERSION INFORMATION
    // ========================================================================
    const val VERSION_CODE = 1
    const val VERSION_NAME = "1.0.0"
    const val VERSION_NAME_SUFFIX_DEBUG = "-debug"
    const val VERSION_NAME_SUFFIX_STAGING = "-staging"
    
    // ========================================================================
    // BUILD TOOLS
    // ========================================================================
    const val BUILD_TOOLS_VERSION = "34.0.0"
    const val NDK_VERSION = "25.2.9519653"
    
    // ========================================================================
    // JAVA/KOTLIN CONFIGURATION
    // ========================================================================
    const val JVM_TARGET = "17"
    val JAVA_VERSION = org.gradle.api.JavaVersion.VERSION_17
    
    // ========================================================================
    // PROGUARD/R8 CONFIGURATION
    // ========================================================================
    const val PROGUARD_ANDROID_OPTIMIZE = "proguard-android-optimize.txt"
    const val PROGUARD_RULES = "proguard-rules.pro"
    const val CONSUMER_PROGUARD_RULES = "consumer-rules.pro"
    
    // ========================================================================
    // TEST CONFIGURATION
    // ========================================================================
    const val TEST_INSTRUMENTATION_RUNNER = "com.spacetec.obd.testing.SpaceTecTestRunner"
    const val TEST_INSTRUMENTATION_RUNNER_HILT = "com.spacetec.obd.testing.HiltTestRunner"
    const val ANDROID_TEST_INSTRUMENTATION_RUNNER = "androidx.test.runner.AndroidJUnitRunner"
    
    // ========================================================================
    // BUILD TYPES
    // ========================================================================
    object BuildTypes {
        const val DEBUG = "debug"
        const val RELEASE = "release"
        const val STAGING = "staging"
        const val BENCHMARK = "benchmark"
    }
    
    // ========================================================================
    // PRODUCT FLAVORS
    // ========================================================================
    object FlavorDimensions {
        const val VERSION = "version"
        const val DISTRIBUTION = "distribution"
    }
    
    object Flavors {
        const val FREE = "free"
        const val PRO = "pro"
        const val ENTERPRISE = "enterprise"
        
        const val GOOGLE_PLAY = "googlePlay"
        const val DIRECT = "direct"
    }
    
    // ========================================================================
    // MODULE NAMESPACES
    // ========================================================================
    object Namespaces {
        const val APP = APPLICATION_ID
        
        // Core modules
        const val CORE_COMMON = "$APPLICATION_ID.core.common"
        const val CORE_DATABASE = "$APPLICATION_ID.core.database"
        const val CORE_NETWORK = "$APPLICATION_ID.core.network"
        const val CORE_DATASTORE = "$APPLICATION_ID.core.datastore"
        const val CORE_SECURITY = "$APPLICATION_ID.core.security"
        const val CORE_LOGGING = "$APPLICATION_ID.core.logging"
        const val CORE_DOMAIN = "$APPLICATION_ID.core.domain"
        const val CORE_DATA = "$APPLICATION_ID.core.data"
        const val CORE_UI = "$APPLICATION_ID.core.ui"
        const val CORE_TESTING = "$APPLICATION_ID.core.testing"
        
        // Protocol modules
        const val PROTOCOL_CORE = "$APPLICATION_ID.protocol.core"
        const val PROTOCOL_OBD = "$APPLICATION_ID.protocol.obd"
        const val PROTOCOL_UDS = "$APPLICATION_ID.protocol.uds"
        const val PROTOCOL_CAN = "$APPLICATION_ID.protocol.can"
        const val PROTOCOL_KLINE = "$APPLICATION_ID.protocol.kline"
        const val PROTOCOL_J1850 = "$APPLICATION_ID.protocol.j1850"
        const val PROTOCOL_ISO9141 = "$APPLICATION_ID.protocol.iso9141"
        const val PROTOCOL_ISO14230 = "$APPLICATION_ID.protocol.iso14230"
        
        // Scanner modules
        const val SCANNER_CORE = "$APPLICATION_ID.scanner.core"
        const val SCANNER_BLUETOOTH = "$APPLICATION_ID.scanner.bluetooth"
        const val SCANNER_WIFI = "$APPLICATION_ID.scanner.wifi"
        const val SCANNER_USB = "$APPLICATION_ID.scanner.usb"
        const val SCANNER_J2534 = "$APPLICATION_ID.scanner.j2534"
        const val SCANNER_ELM327 = "$APPLICATION_ID.scanner.elm327"
        
        // Feature modules
        const val FEATURES_DTC = "$APPLICATION_ID.features.dtc"
        const val FEATURES_LIVEDATA = "$APPLICATION_ID.features.livedata"
        const val FEATURES_FREEZEFRAME = "$APPLICATION_ID.features.freezeframe"
        const val FEATURES_REPORTS = "$APPLICATION_ID.features.reports"
        const val FEATURES_CODING = "$APPLICATION_ID.features.coding"
        const val FEATURES_BIDIRECTIONAL = "$APPLICATION_ID.features.bidirectional"
        const val FEATURES_KEYPROGRAMMING = "$APPLICATION_ID.features.keyprogramming"
        const val FEATURES_ECU = "$APPLICATION_ID.features.ecu"
        const val FEATURES_MAINTENANCE = "$APPLICATION_ID.features.maintenance"
        const val FEATURES_DASHBOARD = "$APPLICATION_ID.features.dashboard"
        const val FEATURES_SETTINGS = "$APPLICATION_ID.features.settings"
        const val FEATURES_CONNECTION = "$APPLICATION_ID.features.connection"
        const val FEATURES_VEHICLEINFO = "$APPLICATION_ID.features.vehicleinfo"
        
        // Vehicle brand modules
        const val VEHICLE_CORE = "$APPLICATION_ID.vehicle.core"
        const val VEHICLE_GENERIC = "$APPLICATION_ID.vehicle.brands.generic"
        
        // Analysis modules
        const val ANALYSIS_CORE = "$APPLICATION_ID.analysis.core"
        const val ANALYSIS_ML = "$APPLICATION_ID.analysis.ml"
        const val ANALYSIS_PATTERNS = "$APPLICATION_ID.analysis.patterns"
        const val ANALYSIS_PREDICTIONS = "$APPLICATION_ID.analysis.predictions"
        
        // Compliance modules
        const val COMPLIANCE_CORE = "$APPLICATION_ID.compliance.core"
        const val COMPLIANCE_CARB = "$APPLICATION_ID.compliance.carb"
        const val COMPLIANCE_EURO6 = "$APPLICATION_ID.compliance.euro6"
        const val COMPLIANCE_EMISSIONS = "$APPLICATION_ID.compliance.emissions"
    }
    
    // ========================================================================
    // KOTLIN COMPILER OPTIONS
    // ========================================================================
    object KotlinOptions {
        val FREE_COMPILER_ARGS = listOf(
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=kotlinx.coroutines.FlowPreview",
            "-opt-in=kotlin.time.ExperimentalTime",
            "-opt-in=kotlin.contracts.ExperimentalContracts",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.animation.ExperimentalAnimationApi",
            "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi",
            "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi"
        )
    }
    
    // ========================================================================
    // BUILD FEATURES
    // ========================================================================
    object BuildFeatures {
        const val COMPOSE = true
        const val BUILD_CONFIG = true
        const val VIEW_BINDING = false
        const val DATA_BINDING = false
        const val AIDL = false
        const val RENDER_SCRIPT = false
        const val SHADERS = false
    }
    
    // ========================================================================
    // LINT OPTIONS
    // ========================================================================
    object LintOptions {
        const val ABORT_ON_ERROR = false
        const val CHECK_RELEASE_BUILDS = true
        const val CHECK_DEPENDENCIES = true
        val DISABLE = setOf(
            "MissingTranslation",
            "ObsoleteLintCustomCheck"
        )
        val WARNING = setOf(
            "InvalidPackage"
        )
    }
    
    // ========================================================================
    // PACKAGING OPTIONS
    // ========================================================================
    object PackagingOptions {
        val EXCLUDED_RESOURCES = setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/DEPENDENCIES",
            "/META-INF/LICENSE",
            "/META-INF/LICENSE.txt",
            "/META-INF/license.txt",
            "/META-INF/NOTICE",
            "/META-INF/NOTICE.txt",
            "/META-INF/notice.txt",
            "/META-INF/ASL2.0",
            "/META-INF/*.kotlin_module",
            "/META-INF/versions/9/previous-compilation-data.bin"
        )
    }
    
    // ========================================================================
    // COMPOSE OPTIONS
    // ========================================================================
    object ComposeOptions {
        const val KOTLIN_COMPILER_EXTENSION_VERSION = "1.5.8"
        const val USE_LIVE_LITERALS = false
        const val INCLUDE_SOURCE_INFORMATION = true
    }
    
    // ========================================================================
    // ROOM DATABASE
    // ========================================================================
    object RoomConfig {
        const val SCHEMA_DIRECTORY = "\$projectDir/schemas"
        const val EXPORT_SCHEMA = true
        const val INCREMENTAL = true
    }
}

/**
 * Extension function to get namespace for a module path
 */
fun String.toNamespace(): String {
    return SpaceTecBuildConfig.APPLICATION_ID + this
        .removePrefix(":")
        .replace(":", ".")
        .replace("-", "")
}
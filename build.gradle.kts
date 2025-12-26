import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

// ============================================================================
// PLUGIN DECLARATIONS
// ============================================================================
plugins {
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.ktlint) apply false
}

// ============================================================================
// PROJECT METADATA
// ============================================================================
val projectGroup = "com.spacetec.automotive"
val projectVersion = "1.0.0"
val projectMinSdk = 26
val projectTargetSdk = 34
val projectCompileSdk = 34
val projectVersionCode = 1

// ============================================================================
// SUBPROJECTS CONFIGURATION
// ============================================================================
subprojects {
    // Apply common configurations to all subprojects
    afterEvaluate {
        // Configure Kotlin compilation options
        tasks.withType<KotlinCompile>().configureEach {
            kotlinOptions {
                jvmTarget = "17"
                freeCompilerArgs = listOf(
                    "-opt-in=kotlin.RequiresOptIn",
                    "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
                    "-opt-in=kotlinx.coroutines.FlowPreview",
                    "-opt-in=kotlin.time.ExperimentalTime",
                    "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
                    "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
                    "-opt-in=androidx.compose.animation.ExperimentalAnimationApi",
                    "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi"
                )
            }
        }
        
        // Configure Java compilation options
        tasks.withType<JavaCompile>().configureEach {
            sourceCompatibility = JavaVersion.VERSION_17.toString()
            targetCompatibility = JavaVersion.VERSION_17.toString()
        }
    }
}

// ============================================================================
// ALL PROJECTS CONFIGURATION
// ============================================================================
allprojects {
    // Project group and version
    group = projectGroup
    version = projectVersion
    

    
    // Configure static analysis
    apply(plugin = "io.gitlab.arturbosch.detekt")

    // Detekt configuration
    configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        buildUponDefaultConfig = true
        allRules = false
        config.setFrom(files("${rootProject.projectDir}/config/detekt/detekt.yml"))

        // This repository currently contains a large number of detekt findings.
        // The allowed issue count is configured via `config/detekt/detekt.yml` (build.maxIssues).

        val baselineFile = file("${rootProject.projectDir}/config/detekt/baseline.xml")
        if (baselineFile.exists()) {
            baseline = baselineFile
        }
    }
}

// ============================================================================
// CLEAN TASK
// ============================================================================
tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}

// ============================================================================
// CODE QUALITY TASKS
// ============================================================================
tasks.register("runAllTests") {
    description = "Runs all unit tests across all modules"
    group = "verification"
    
    dependsOn(subprojects.mapNotNull { it.tasks.findByName("test") })
}

tasks.register("runAllLintChecks") {
    description = "Runs all lint checks across all modules"
    group = "verification"
    
    dependsOn(subprojects.mapNotNull { it.tasks.findByName("lint") })
}

tasks.register("runAllDetektChecks") {
    description = "Runs detekt static analysis on all modules"
    group = "verification"
    
    dependsOn(subprojects.mapNotNull { it.tasks.findByName("detekt") })
}

tasks.register("generateAllDocumentation") {
    description = "Generates documentation for all modules"
    group = "documentation"
    
    dependsOn(subprojects.mapNotNull { it.tasks.findByName("dokkaHtml") })
}

// ============================================================================
// PROJECT INFO TASK
// ============================================================================
tasks.register("projectInfo") {
    description = "Displays SpaceTec project information"
    group = "help"
    
    doLast {
        println("""
            |============================================================
            | SpaceTec Automotive DTC System
            |============================================================
            | Version: $projectVersion
            | Group: $projectGroup
            | Min SDK: $projectMinSdk
            | Target SDK: $projectTargetSdk
            | Compile SDK: $projectCompileSdk
            |============================================================
            | Modules:
            |   - Core Modules: ${subprojects.count { it.path.startsWith(":core") }}
            |   - Protocol Modules: ${subprojects.count { it.path.startsWith(":protocol") }}
            |   - Scanner Modules: ${subprojects.count { it.path.startsWith(":scanner") }}
            |   - Feature Modules: ${subprojects.count { it.path.startsWith(":features") }}
            |   - Vehicle Brand Modules: ${subprojects.count { it.path.startsWith(":vehicle") }}
            |   - Analysis Modules: ${subprojects.count { it.path.startsWith(":analysis") }}
            |   - Compliance Modules: ${subprojects.count { it.path.startsWith(":compliance") }}
            |   - Total: ${subprojects.size}
            |============================================================
            | Architecture: Clean Architecture with Multi-Module Structure
            | UI Framework: Jetpack Compose
            | Language: Kotlin
            | Dependency Injection: Hilt
            | Database: Room with FTS4
            | Networking: Retrofit + OkHttp
            |============================================================
            | Supported Scanner Types:
            |   - Bluetooth (ELM327, OBDLink, etc.)
            |   - WiFi (Wireless OBD adapters)
            |   - USB (Direct USB OBD interfaces)
            |   - J2534 (Professional PassThru devices)
            |============================================================
            | Supported Protocols:
            |   - OBD-II (ISO 15031)
            |   - UDS (ISO 14229)
            |   - CAN (ISO 15765)
            |   - K-Line (ISO 9141, ISO 14230)
            |   - J1850 (VPW, PWM)
            |============================================================
            | Supported Manufacturers:
            |   - European: Audi, BMW, Mercedes, VW, Porsche, Volvo
            |   - Japanese: Toyota, Lexus, Honda, Nissan, Mazda, Subaru
            |   - American: Ford, GM, Chevrolet, Chrysler, Dodge, Jeep
            |   - Korean: Hyundai, Kia
            |   - British: Jaguar, Land Rover
            |============================================================
        """.trimMargin())
    }
}

// ============================================================================
// DEPENDENCY UPDATES CHECK
// ============================================================================
tasks.register("checkDependencyUpdates") {
    description = "Checks for available dependency updates"
    group = "help"
    
    doLast {
        println("Run './gradlew dependencyUpdates' to check for updates")
    }
}

// ============================================================================
// MODULE GRAPH GENERATION
// ============================================================================
tasks.register("generateModuleGraph") {
    description = "Generates a module dependency graph"
    group = "documentation"
    
    doLast {
        val graphFile = file("${rootProject.projectDir}/docs/module-graph.md")
        graphFile.parentFile.mkdirs()
        
        val graphContent = buildString {
            appendLine("# SpaceTec Module Dependency Graph")
            appendLine()
            appendLine("```mermaid")
            appendLine("graph TD")
            appendLine("    app[\":app\"]")
            appendLine()
            appendLine("    subgraph Core")
            appendLine("        core_common[\":core:common\"]")
            appendLine("        core_domain[\":core:domain\"]")
            appendLine("        core_data[\":core:data\"]")
            appendLine("        core_database[\":core:database\"]")
            appendLine("        core_network[\":core:network\"]")
            appendLine("        core_ui[\":core:ui\"]")
            appendLine("        core_security[\":core:security\"]")
            appendLine("        core_logging[\":core:logging\"]")
            appendLine("    end")
            appendLine()
            appendLine("    subgraph Protocol")
            appendLine("        protocol_core[\":protocol:core\"]")
            appendLine("        protocol_obd[\":protocol:obd\"]")
            appendLine("        protocol_uds[\":protocol:uds\"]")
            appendLine("        protocol_can[\":protocol:can\"]")
            appendLine("    end")
            appendLine()
            appendLine("    subgraph Scanner")
            appendLine("        scanner_core[\":scanner:core\"]")
            appendLine("        scanner_bluetooth[\":scanner:bluetooth\"]")
            appendLine("        scanner_wifi[\":scanner:wifi\"]")
            appendLine("        scanner_usb[\":scanner:usb\"]")
            appendLine("        scanner_j2534[\":scanner:j2534\"]")
            appendLine("    end")
            appendLine()
            appendLine("    subgraph Features")
            appendLine("        features_dtc[\":features:dtc\"]")
            appendLine("        features_livedata[\":features:livedata\"]")
            appendLine("        features_reports[\":features:reports\"]")
            appendLine("    end")
            appendLine()
            appendLine("    app --> core_common")
            appendLine("    app --> core_ui")
            appendLine("    app --> features_dtc")
            appendLine("    app --> features_livedata")
            appendLine("    app --> scanner_core")
            appendLine()
            appendLine("    features_dtc --> core_domain")
            appendLine("    features_dtc --> protocol_obd")
            appendLine()
            appendLine("    scanner_bluetooth --> scanner_core")
            appendLine("    scanner_wifi --> scanner_core")
            appendLine("    scanner_usb --> scanner_core")
            appendLine()
            appendLine("    protocol_obd --> protocol_core")
            appendLine("    protocol_uds --> protocol_core")
            appendLine("```")
        }
        
        graphFile.writeText(graphContent)
        println("Module graph generated at: ${graphFile.absolutePath}")
    }
}

// ============================================================================
// BUILD CONFIGURATION EXTENSION
// ============================================================================
extra.apply {
    set("projectMinSdk", projectMinSdk)
    set("projectTargetSdk", projectTargetSdk)
    set("projectCompileSdk", projectCompileSdk)
    set("projectVersionCode", projectVersionCode)
    set("projectVersionName", projectVersion)
    set("javaVersion", JavaVersion.VERSION_17)
}
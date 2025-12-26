/**
 * SpaceTec Automotive DTC System
 * OBD-II Protocol Module
 * 
 * This module implements the OBD-II (On-Board Diagnostics) protocol
 * according to SAE J1979 / ISO 15031-5 standards. It provides:
 * 
 * - Service 01-0A implementations
 * - DTC reading and clearing
 * - Freeze frame data access
 * - PID decoding and scaling
 * - Multi-protocol support (CAN, K-Line, J1850)
 * - ECU detection and communication
 */
plugins {
    id("spacetec.android.library")
    id("spacetec.android.hilt")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.spacetec.obd.protocol.obd"
    
    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }
    
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    // ========================================================================
    // PROJECT DEPENDENCIES
    // ========================================================================
    api(project(":core:common"))
    api(project(":core:domain"))
    api(project(":protocol:core"))
    
    // ========================================================================
    // KOTLIN
    // ========================================================================
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    
    // ========================================================================
    // DEPENDENCY INJECTION
    // ========================================================================
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    
    // ========================================================================
    // LOGGING
    // ========================================================================
    implementation(libs.timber)
    
    // ========================================================================
    // TESTING
    // ========================================================================
    testImplementation(libs.junit)
    testImplementation(libs.junit5.api)
    testImplementation(libs.junit5.engine)
    testImplementation(libs.junit5.params)
    testImplementation(libs.mockk)
    testImplementation(libs.truth)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
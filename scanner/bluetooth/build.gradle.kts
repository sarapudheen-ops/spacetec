/**
 * SpaceTec Automotive DTC System
 * Bluetooth Scanner Module
 * 
 * This module implements Bluetooth connectivity for OBD-II scanners.
 * It supports both Classic Bluetooth (SPP) and Bluetooth Low Energy (BLE).
 * 
 * Features:
 * - Device discovery and pairing
 * - SPP (Serial Port Profile) connection for ELM327
 * - BLE GATT connection for BLE-based adapters
 * - Connection state management
 * - Auto-reconnection
 * - Background operation support
 */
plugins {
    id("spacetec.android.library")
    id("spacetec.android.hilt")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.spacetec.obd.scanner.bluetooth"
    
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
    api(project(":scanner:core"))
    
    // ========================================================================
    // KOTLIN
    // ========================================================================
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    
    // ========================================================================
    // ANDROIDX
    // ========================================================================
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.annotation)
    
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
    testImplementation(libs.mockk)
    testImplementation(libs.truth)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
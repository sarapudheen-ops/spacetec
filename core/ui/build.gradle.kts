// core/ui/build.gradle.kts
/**
 * SpaceTec Automotive DTC System
 * Core UI Module
 * 
 * This module provides the shared UI components and theming
 * for the entire application using Jetpack Compose. It includes:
 * 
 * - Material 3 theme configuration
 * - Color schemes (light/dark)
 * - Typography definitions
 * - Common composable components
 * - Icons and resources
 * - Animation utilities
 */
plugins {
    id("spacetec.android.library.compose")
    id("spacetec.android.hilt")
}

android {
    namespace = "com.spacetec.obd.core.ui"
    
    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }
    
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // ========================================================================
    // PROJECT DEPENDENCIES
    // ========================================================================
    api(project(":core:common"))
    api(project(":core:domain"))
    
    // ========================================================================
    // COMPOSE
    // ========================================================================
    api(platform(libs.compose.bom))
    api(libs.compose.ui)
    api(libs.compose.ui.graphics)
    api(libs.compose.ui.tooling.preview)
    api(libs.compose.material3)
    api(libs.compose.material3.window.size)
    api(libs.compose.material.icons.core)
    api(libs.compose.material.icons.extended)
    api(libs.compose.animation)
    api(libs.compose.animation.graphics)
    api(libs.compose.foundation)
    api(libs.compose.foundation.layout)
    api(libs.compose.runtime)
    api(libs.compose.runtime.livedata)
    
    debugApi(libs.compose.ui.tooling)
    debugApi(libs.compose.ui.test.manifest)
    
    // ========================================================================
    // LIFECYCLE
    // ========================================================================
    api(libs.androidx.lifecycle.runtime.compose)
    api(libs.androidx.lifecycle.viewmodel.compose)
    
    // ========================================================================
    // NAVIGATION
    // ========================================================================
    api(libs.androidx.navigation.compose)
    api(libs.androidx.hilt.navigation.compose)
    
    // ========================================================================
    // IMAGE LOADING
    // ========================================================================
    api(libs.coil.compose)
    
    // ========================================================================
    // KOTLINX
    // ========================================================================
    implementation(libs.kotlinx.collections.immutable)
    
    // ========================================================================
    // DEPENDENCY INJECTION
    // ========================================================================
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    
    // ========================================================================
    // TESTING
    // ========================================================================
    testImplementation(libs.junit)
    androidTestImplementation(libs.compose.ui.test.junit4)
}
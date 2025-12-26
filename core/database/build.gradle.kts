// core/database/build.gradle.kts
/**
 * SpaceTec Automotive DTC System
 * Core Database Module
 * 
 * This module provides the local database layer using Room.
 * It includes:
 * 
 * - Room database configuration with SQLCipher encryption
 * - Entity definitions for DTCs, vehicles, sessions, etc.
 * - DAO interfaces for data access
 * - Type converters for complex types
 * - Database migrations
 * - FTS4 full-text search for DTC definitions
 * - Pre-populated DTC database (50,000+ codes)
 */
plugins {
    id("spacetec.android.library")
    id("spacetec.android.hilt")
    id("spacetec.android.room")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.spacetec.core.database"
    
    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }
    
    buildFeatures {
        buildConfig = true
    }
    
    sourceSets {
        getByName("androidTest").assets.srcDirs("$projectDir/schemas")
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    // ========================================================================
    // PROJECT DEPENDENCIES
    // ========================================================================
    api(project(":core:common"))
    // NOTE: Do not add dependency on domain module to avoid circular dependency
    // Domain module should depend on database module instead
    
    // ========================================================================
    // ROOM DATABASE
    // ========================================================================
    api(libs.androidx.room.runtime)
    api(libs.androidx.room.ktx)
    api(libs.androidx.room.paging)
    ksp(libs.androidx.room.compiler)
    
    // ========================================================================
    // DATABASE ENCRYPTION - Temporarily disabled
    // ========================================================================
    // implementation(libs.sqlcipher.android)
    implementation(libs.androidx.sqlite.ktx)
    
    // ========================================================================
    // KOTLIN
    // ========================================================================
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)
    
    // ========================================================================
    // PAGING
    // ========================================================================
    implementation(libs.androidx.paging.runtime.ktx)
    
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
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.room.testing)
    
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.truth)
}
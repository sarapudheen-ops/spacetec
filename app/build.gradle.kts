/**
 * SpaceTec Automotive DTC System
 * Main Application Module
 * 
 * This module contains the main application entry point,
 * UI navigation, dependency injection setup, and core
 * application configuration.
 */
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.spacetec.obd"
    compileSdk = 34
    
    defaultConfig {
        applicationId = "com.spacetec.obd"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }
    
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    buildFeatures {
        compose = true
        buildConfig = true
    }
    
    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.compose.compiler.get()
    }
    
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }
}

dependencies {
    // ========================================================================
    // PROJECT DEPENDENCIES
    // ========================================================================
    // Core modules
    implementation(project(":core:common"))
    implementation(project(":core:domain"))
    implementation(project(":core:logging"))
    implementation(project(":core:ui"))
    implementation(project(":core:security"))
    implementation(project(":core:datastore"))
    implementation(project(":core:network"))
    // implementation(project(":core:database")) // Commented out due to KSP errors

    // Feature modules used by navigation
    implementation(project(":features:dashboard"))
    implementation(project(":features:connection"))
    implementation(project(":features:dtc"))
    implementation(project(":features:livedata"))
    implementation(project(":features:reports"))

    // ========================================================================
    // CORE LIBRARY DESUGARING
    // ========================================================================
    coreLibraryDesugaring(libs.android.desugar.jdk.libs)
    
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
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    
    // ========================================================================
    // COMPOSE
    // ========================================================================
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    
    // ========================================================================
    // DEPENDENCY INJECTION
    // ========================================================================
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    
    // ========================================================================
    // LOGGING
    // ========================================================================
    implementation(libs.timber)
    
    // ========================================================================
    // TESTING
    // ========================================================================
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
}

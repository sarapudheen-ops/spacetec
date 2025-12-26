plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.spacetec.obd.core.domain"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":core:common"))
    // api(project(":core:database"))  // Commented out due to KSP errors
    // implementation(project(":protocol:core")) // Disabled due to scanner dependency
    // implementation(project(":scanner:core")) // Scanner module is currently commented out due to AGP 8.x circular dependency bug
    // implementation(project(":vehicle:core")) // Removed to avoid circular dependency

    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)
    implementation(libs.javax.inject)

    // Android dependencies for Bluetooth interfaces
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    // Android platform dependencies for Bluetooth
    // The android.bluetooth classes are part of the Android framework
    // and should be available through the Android SDK

    testImplementation(libs.junit)
}
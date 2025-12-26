plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.spacetec.obd.scanner.devices"
    compileSdk = 35 // Use your target SDK

    defaultConfig {
        minSdk = 21 // Or your minimum SDK

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
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
    kotlinOptions {
        jvmTarget = "17"
    }
}

// Workaround for AGP 8.x circular dependency bug
project.afterEvaluate {
    val bundleTasks = tasks.matching { it.name.startsWith("bundleLibCompileToJar") }
    bundleTasks.forEach { task ->
        task.enabled = false
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:domain"))
    implementation(project(":transport:contract"))
    implementation(project(":protocol:core"))
    // Add other dependencies as needed
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
}
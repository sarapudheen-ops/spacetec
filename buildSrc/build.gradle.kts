plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation(libs.android.gradle.plugin)
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.hilt.gradle.plugin)
    implementation("org.jetbrains.kotlin:kotlin-serialization:1.9.22")
    implementation("com.google.devtools.ksp:symbol-processing-gradle-plugin:1.9.22-1.0.17")
    implementation("androidx.room:room-gradle-plugin:2.6.1")
}

// Enable version catalog access in buildSrc
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)
}

gradlePlugin {
    plugins {
        register("spacetec.android.application") {
            id = "spacetec.android.application"
            implementationClass = "SpaceTecAndroidApplicationPlugin"
        }
        register("spacetec.android.library") {
            id = "spacetec.android.library"
            implementationClass = "SpaceTecAndroidLibraryPlugin"
        }
        register("spacetec.android.library.compose") {
            id = "spacetec.android.library.compose"
            implementationClass = "SpaceTecAndroidLibraryComposePlugin"
        }
        register("spacetec.android.feature") {
            id = "spacetec.android.feature"
            implementationClass = "SpaceTecAndroidFeaturePlugin"
        }
        register("spacetec.android.hilt") {
            id = "spacetec.android.hilt"
            implementationClass = "SpaceTecAndroidHiltPlugin"
        }
        register("spacetec.android.room") {
            id = "spacetec.android.room"
            implementationClass = "SpaceTecAndroidRoomPlugin"
        }
        register("spacetec.kotlin.library") {
            id = "spacetec.kotlin.library"
            implementationClass = "SpaceTecKotlinLibraryPlugin"
        }
    }
}
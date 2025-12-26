plugins {
    id("spacetec.android.feature")
}

android {
    namespace = "com.spacetec.features.connection"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:domain"))
    implementation(project(":core:ui"))
    // Temporarily disabled due to scanner:core circular dependency
    // implementation(project(":scanner:core"))
}

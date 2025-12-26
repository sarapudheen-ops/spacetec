plugins {
    id("spacetec.android.feature")
}

android {
    namespace = "com.spacetec.features.ecu"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:domain"))
}

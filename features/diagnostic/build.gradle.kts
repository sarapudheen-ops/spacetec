plugins {
    id("spacetec.android.feature")
}

android {
    namespace = "com.spacetec.features.diagnostic"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:domain"))
}

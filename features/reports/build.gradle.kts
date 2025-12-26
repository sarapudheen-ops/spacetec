plugins {
    id("spacetec.android.feature")
}

android {
    namespace = "com.spacetec.features.reports"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:domain"))
}

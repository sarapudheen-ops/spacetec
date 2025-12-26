plugins {
    id("spacetec.android.feature")
}

android {
    namespace = "com.spacetec.features.livedata"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:domain"))
}

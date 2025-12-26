plugins {
    id("spacetec.android.library")
    id("spacetec.android.hilt")
}

android {
    namespace = "com.spacetec.obd.core.data"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:domain"))
    implementation(project(":core:database"))
}

plugins {
    id("spacetec.android.library.compose")
}

android {
    namespace = "com.spacetec.obd.dtc"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:domain"))
    implementation(project(":core:ui"))

    // ViewModel + Compose integration
    implementation(libs.androidx.lifecycle.viewmodel.compose)
}

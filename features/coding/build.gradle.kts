plugins {
    id("spacetec.android.feature")
}

android {
    namespace = "com.spacetec.features.coding"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:domain"))
    implementation(project(":protocol:uds"))
    implementation(project(":protocol:safety"))
    implementation(project(":protocol:security"))
}

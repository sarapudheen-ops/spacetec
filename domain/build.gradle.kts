plugins {
    id("org.jetbrains.kotlin.jvm")
    // id("java-library") // Uncomment if pure Java/JVM
    id("maven-publish")
}

dependencies {
    // Kotlin stdlib is added automatically by the Kotlin plugin
    // Add other dependencies as needed
    testImplementation(libs.junit)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

publishing {
    publications {
        create<MavenPublication>("default") {
            groupId = "com.spacetec.domain"
            artifactId = "library" // Or derive from module name
            version = "1.0.0"
            from(components["java"])
        }
    }
}

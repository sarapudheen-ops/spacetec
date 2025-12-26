import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * Convention plugin for Android Application modules in SpaceTec project.
 * 
 * Applies common configurations for application modules including:
 * - Android SDK versions
 * - Application ID and versioning
 * - Build types (debug, release, staging)
 * - ProGuard/R8 configuration
 * - Compose setup
 * - Hilt integration
 */
class SpaceTecAndroidApplicationPlugin : Plugin<Project> {
    
    override fun apply(target: Project) {
        with(target) {
            // Apply required plugins
            with(pluginManager) {
                apply("com.android.application")
                apply("org.jetbrains.kotlin.android")
            }
            
            // Configure Android extension
            extensions.configure<ApplicationExtension> {
                configureAndroidApplication(this)
            }
        }
    }
    
    private fun Project.configureAndroidApplication(extension: ApplicationExtension) {
        extension.apply {
            namespace = "com.spacetec.automotive.${project.path.replace(":", ".").replace("-", "")}"
            
            compileSdk = SpaceTecBuildConfig.COMPILE_SDK
            
            defaultConfig {
                minSdk = SpaceTecBuildConfig.MIN_SDK
                targetSdk = SpaceTecBuildConfig.TARGET_SDK
                
                versionCode = SpaceTecBuildConfig.VERSION_CODE
                versionName = SpaceTecBuildConfig.VERSION_NAME
                
                testInstrumentationRunner = SpaceTecBuildConfig.TEST_INSTRUMENTATION_RUNNER_HILT
                
                vectorDrawables {
                    useSupportLibrary = true
                }
            }
            
            buildTypes {
                getByName(SpaceTecBuildConfig.BuildTypes.DEBUG) {
                    isDebuggable = true
                    applicationIdSuffix = SpaceTecBuildConfig.VERSION_NAME_SUFFIX_DEBUG
                    versionNameSuffix = SpaceTecBuildConfig.VERSION_NAME_SUFFIX_DEBUG
                    isMinifyEnabled = false
                }
                
                getByName(SpaceTecBuildConfig.BuildTypes.RELEASE) {
                    isMinifyEnabled = true
                    isShrinkResources = true
                    proguardFiles(
                        getDefaultProguardFile(SpaceTecBuildConfig.PROGUARD_ANDROID_OPTIMIZE),
                        SpaceTecBuildConfig.PROGUARD_RULES
                    )
                }
            }
            
            compileOptions {
                sourceCompatibility = SpaceTecBuildConfig.JAVA_VERSION
                targetCompatibility = SpaceTecBuildConfig.JAVA_VERSION
                isCoreLibraryDesugaringEnabled = true
            }
            
            buildFeatures {
                compose = SpaceTecBuildConfig.BuildFeatures.COMPOSE
            }
            
            composeOptions {
                kotlinCompilerExtensionVersion = SpaceTecBuildConfig.ComposeOptions.KOTLIN_COMPILER_EXTENSION_VERSION
            }
            
            packaging {
                resources {
                    excludes += SpaceTecBuildConfig.PackagingOptions.EXCLUDED_RESOURCES
                }
            }
        }
        
        // Add common dependencies
        dependencies {
            add("coreLibraryDesugaring", libs.findLibrary("android-desugar-jdk-libs").get())
            add("implementation", libs.findLibrary("kotlin-stdlib").get())
            add("implementation", libs.findLibrary("kotlinx-coroutines-core").get())
            add("implementation", libs.findLibrary("kotlinx-coroutines-android").get())
            add("implementation", libs.findLibrary("timber").get())

            // Testing dependencies
            add("testImplementation", libs.findLibrary("junit").get())
            add("testImplementation", libs.findLibrary("mockk").get())
            add("testImplementation", libs.findLibrary("truth").get())
            add("testImplementation", libs.findLibrary("kotlinx-coroutines-test").get())
            add("testImplementation", libs.findLibrary("turbine").get())

            add("androidTestImplementation", libs.findLibrary("androidx-test-ext-junit").get())
            add("androidTestImplementation", libs.findLibrary("androidx-test-espresso-core").get())
        }
    }

    // Extension function to access version catalog
    private val Project.libs
        get() = extensions.getByType(org.gradle.api.artifacts.VersionCatalogsExtension::class.java)
            .named("libs")
}
import com.android.build.gradle.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * Convention plugin for Feature modules in SpaceTec project.
 * 
 * Feature modules are UI-facing modules that contain screens,
 * ViewModels, and feature-specific components. This plugin
 * configures Compose, Hilt, and common feature dependencies.
 */
class SpaceTecAndroidFeaturePlugin : Plugin<Project> {
    
    override fun apply(target: Project) {
        with(target) {
            // Apply required plugins
            with(pluginManager) {
                apply("com.android.library")
                apply("org.jetbrains.kotlin.android")
                apply("com.google.devtools.ksp")
                apply("com.google.dagger.hilt.android")
            }
            
            // Configure Android extension
            extensions.configure<LibraryExtension> {
                configureFeatureModule(this)
            }
            
            // Add feature dependencies
            configureFeatureDependencies()
        }
    }
    
    private fun Project.configureFeatureModule(extension: LibraryExtension) {
        extension.apply {
            compileSdk = SpaceTecBuildConfig.COMPILE_SDK
            
            defaultConfig {
                minSdk = SpaceTecBuildConfig.MIN_SDK
                testInstrumentationRunner = SpaceTecBuildConfig.TEST_INSTRUMENTATION_RUNNER_HILT
                consumerProguardFiles(SpaceTecBuildConfig.CONSUMER_PROGUARD_RULES)
                
                vectorDrawables {
                    useSupportLibrary = true
                }
            }
            
            buildTypes {
                getByName(SpaceTecBuildConfig.BuildTypes.DEBUG) {
                    isMinifyEnabled = false
                }
                
                getByName(SpaceTecBuildConfig.BuildTypes.RELEASE) {
                    isMinifyEnabled = true
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
                compose = true
                buildConfig = true
            }
            
            composeOptions {
                kotlinCompilerExtensionVersion = SpaceTecBuildConfig.ComposeOptions.KOTLIN_COMPILER_EXTENSION_VERSION
            }
            
            packaging {
                resources {
                    excludes += SpaceTecBuildConfig.PackagingOptions.EXCLUDED_RESOURCES
                }
            }
            
            testOptions {
                unitTests {
                    isIncludeAndroidResources = true
                    isReturnDefaultValues = true
                }
            }
        }
    }
    
    private fun Project.configureFeatureDependencies() {
        val libs = extensions.getByType(org.gradle.api.artifacts.VersionCatalogsExtension::class.java)
            .named("libs")
        
        dependencies {
            // Core library desugaring
            add("coreLibraryDesugaring", libs.findLibrary("android-desugar-jdk-libs").get())
            
            // Core module dependencies
            add("implementation", project(":core:common"))
            add("implementation", project(":core:domain"))
            add("implementation", project(":core:ui"))
            
            // Kotlin
            add("implementation", libs.findLibrary("kotlin-stdlib").get())
            add("implementation", libs.findLibrary("kotlinx-coroutines-core").get())
            add("implementation", libs.findLibrary("kotlinx-coroutines-android").get())
            add("implementation", libs.findLibrary("kotlinx-collections-immutable").get())
            
            // Compose BOM and UI
            add("implementation", platform(libs.findLibrary("compose-bom").get()))
            add("implementation", libs.findLibrary("compose-ui").get())
            add("implementation", libs.findLibrary("compose-ui-graphics").get())
            add("implementation", libs.findLibrary("compose-ui-tooling-preview").get())
            add("implementation", libs.findLibrary("compose-material3").get())
            add("implementation", libs.findLibrary("compose-material3-window-size").get())
            add("implementation", libs.findLibrary("compose-material-icons-core").get())
            add("implementation", libs.findLibrary("compose-material-icons-extended").get())
            add("implementation", libs.findLibrary("compose-animation").get())
            add("implementation", libs.findLibrary("compose-foundation").get())
            add("implementation", libs.findLibrary("compose-runtime").get())
            
            add("debugImplementation", libs.findLibrary("compose-ui-tooling").get())
            add("debugImplementation", libs.findLibrary("compose-ui-test-manifest").get())
            
            // Lifecycle
            add("implementation", libs.findLibrary("androidx-lifecycle-runtime-ktx").get())
            add("implementation", libs.findLibrary("androidx-lifecycle-runtime-compose").get())
            add("implementation", libs.findLibrary("androidx-lifecycle-viewmodel-ktx").get())
            add("implementation", libs.findLibrary("androidx-lifecycle-viewmodel-compose").get())
            add("implementation", libs.findLibrary("androidx-lifecycle-viewmodel-savedstate").get())
            
            // Navigation
            add("implementation", libs.findLibrary("androidx-navigation-compose").get())
            
            // Hilt
            add("implementation", libs.findLibrary("hilt-android").get())
            add("ksp", libs.findLibrary("hilt-android-compiler").get())
            add("implementation", libs.findLibrary("androidx-hilt-navigation-compose").get())
            
            // Coil for images
            add("implementation", libs.findLibrary("coil-compose").get())
            
            // Timber for logging
            add("implementation", libs.findLibrary("timber").get())
            
            // Testing
            add("testImplementation", libs.findLibrary("junit").get())
            add("testImplementation", libs.findLibrary("mockk").get())
            add("testImplementation", libs.findLibrary("truth").get())
            add("testImplementation", libs.findLibrary("kotlinx-coroutines-test").get())
            add("testImplementation", libs.findLibrary("turbine").get())
            add("testImplementation", libs.findLibrary("androidx-arch-core-testing").get())
            
            add("androidTestImplementation", libs.findLibrary("androidx-test-ext-junit").get())
            add("androidTestImplementation", libs.findLibrary("androidx-test-espresso-core").get())
            add("androidTestImplementation", libs.findLibrary("hilt-android-testing").get())
            add("androidTestImplementation", platform(libs.findLibrary("compose-bom").get()))
            add("androidTestImplementation", libs.findLibrary("compose-ui-test-junit4").get())
            add("kspAndroidTest", libs.findLibrary("hilt-android-compiler").get())
        }
    }
}
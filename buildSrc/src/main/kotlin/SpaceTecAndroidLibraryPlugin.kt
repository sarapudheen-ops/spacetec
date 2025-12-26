import com.android.build.gradle.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * Convention plugin for Android Library modules in SpaceTec project.
 * 
 * Applies common configurations for library modules including:
 * - Android SDK versions
 * - Kotlin compiler options
 * - Common dependencies
 * - ProGuard configuration
 * - Test configuration
 */
class SpaceTecAndroidLibraryPlugin : Plugin<Project> {
    
    override fun apply(target: Project) {
        with(target) {
            // Apply required plugins
            with(pluginManager) {
                apply("com.android.library")
                apply("org.jetbrains.kotlin.android")
            }
            
            // Configure Android extension
            extensions.configure<LibraryExtension> {
                configureAndroidLibrary(this)
            }
        }
    }
    
    private fun Project.configureAndroidLibrary(extension: LibraryExtension) {
        extension.apply {
            compileSdk = SpaceTecBuildConfig.COMPILE_SDK
            
            defaultConfig {
                minSdk = SpaceTecBuildConfig.MIN_SDK
                
                testInstrumentationRunner = SpaceTecBuildConfig.ANDROID_TEST_INSTRUMENTATION_RUNNER
                
                consumerProguardFiles(SpaceTecBuildConfig.CONSUMER_PROGUARD_RULES)
                
                // Enable vector drawable support
                vectorDrawables {
                    useSupportLibrary = true
                }
                
                // Room schema export configuration
                javaCompileOptions {
                    annotationProcessorOptions {
                        arguments += mapOf(
                            "room.schemaLocation" to "$projectDir/schemas",
                            "room.incremental" to "true",
                            "room.expandProjection" to "true"
                        )
                    }
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
                // Disabled to avoid circular dependency with AGP 8.x
                // isCoreLibraryDesugaringEnabled = true
            }
            
            kotlinOptions {
                jvmTarget = SpaceTecBuildConfig.JVM_TARGET
                freeCompilerArgs = SpaceTecBuildConfig.KotlinOptions.FREE_COMPILER_ARGS
            }
            
            buildFeatures {
                buildConfig = SpaceTecBuildConfig.BuildFeatures.BUILD_CONFIG
            }
            
            packaging {
                resources {
                    excludes += SpaceTecBuildConfig.PackagingOptions.EXCLUDED_RESOURCES
                }
            }
            
            lint {
                abortOnError = SpaceTecBuildConfig.LintOptions.ABORT_ON_ERROR
                checkReleaseBuilds = SpaceTecBuildConfig.LintOptions.CHECK_RELEASE_BUILDS
                checkDependencies = SpaceTecBuildConfig.LintOptions.CHECK_DEPENDENCIES
                disable += SpaceTecBuildConfig.LintOptions.DISABLE
                warning += SpaceTecBuildConfig.LintOptions.WARNING
            }
            
            testOptions {
                unitTests {
                    isIncludeAndroidResources = true
                    isReturnDefaultValues = true
                }
                animationsDisabled = true
            }
        }
        
        // Add common dependencies
        dependencies {
            // Removed desugaring dependency to fix circular dependency
            // add("coreLibraryDesugaring", libs.findLibrary("android-desugar-jdk-libs").get())
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

/**
 * Extension to configure Kotlin options in Android extension
 */
private fun LibraryExtension.kotlinOptions(block: org.jetbrains.kotlin.gradle.dsl.KotlinJvmOptions.() -> Unit) {
    (this as org.gradle.api.plugins.ExtensionAware).extensions.configure("kotlinOptions", block)
}
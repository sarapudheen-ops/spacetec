import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/**
 * Convention plugin for Kotlin Library modules in SpaceTec project.
 * 
 * Applies common configurations for pure Kotlin modules including:
 * - JVM target configuration
 * - Kotlin compiler options
 * - Common dependencies
 */
class SpaceTecKotlinLibraryPlugin : Plugin<Project> {
    
    override fun apply(target: Project) {
        with(target) {
            // Apply required plugins
            with(pluginManager) {
                apply("org.jetbrains.kotlin.jvm")
            }
            
            // Configure Kotlin compilation
            extensions.configure<KotlinProjectExtension> {
                jvmToolchain(17)
            }
            
            // Add common dependencies
            dependencies {
                add("implementation", libs.findLibrary("kotlin-stdlib").get())
                add("implementation", libs.findLibrary("kotlinx-coroutines-core").get())
                add("implementation", libs.findLibrary("timber").get())
                
                // Testing dependencies
                add("testImplementation", libs.findLibrary("junit").get())
                add("testImplementation", libs.findLibrary("mockk").get())
                add("testImplementation", libs.findLibrary("truth").get())
                add("testImplementation", libs.findLibrary("kotlinx-coroutines-test").get())
            }
        }
    }
    
    // Extension function to access version catalog
    private val Project.libs
        get() = extensions.getByType(org.gradle.api.artifacts.VersionCatalogsExtension::class.java)
            .named("libs")
}
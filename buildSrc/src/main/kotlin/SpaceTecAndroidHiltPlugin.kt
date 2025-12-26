import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * Convention plugin for Hilt integration in SpaceTec project.
 * 
 * Applies common Hilt configurations including:
 * - Hilt Android plugin
 * - Hilt dependencies
 * - KSP annotation processing
 */
class SpaceTecAndroidHiltPlugin : Plugin<Project> {
    
    override fun apply(target: Project) {
        with(target) {
            // Apply required plugins
            with(pluginManager) {
                apply("com.google.devtools.ksp")
                apply("com.google.dagger.hilt.android")
            }
            
            // Add Hilt dependencies
            dependencies {
                add("implementation", libs.findLibrary("hilt-android").get())
                add("ksp", libs.findLibrary("hilt-android-compiler").get())
                add("testImplementation", libs.findLibrary("hilt-android-testing").get())
                add("kspTest", libs.findLibrary("hilt-android-compiler").get())
                add("androidTestImplementation", libs.findLibrary("hilt-android-testing").get())
                add("kspAndroidTest", libs.findLibrary("hilt-android-compiler").get())
            }
        }
    }
    
    // Extension function to access version catalog
    private val Project.libs
        get() = extensions.getByType(org.gradle.api.artifacts.VersionCatalogsExtension::class.java)
            .named("libs")
}
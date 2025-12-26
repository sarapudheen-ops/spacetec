import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * Convention plugin for Room database integration in SpaceTec project.
 * 
 * Applies common Room configurations including:
 * - Room dependencies
 * - Schema export configuration
 * - KSP annotation processing
 */
class SpaceTecAndroidRoomPlugin : Plugin<Project> {
    
    override fun apply(target: Project) {
        with(target) {
            // Apply required plugins
            with(pluginManager) {
                apply("androidx.room")
                apply("com.google.devtools.ksp")
            }
            
            // Add Room dependencies
            dependencies {
                add("implementation", libs.findLibrary("androidx-room-runtime").get())
                add("implementation", libs.findLibrary("androidx-room-ktx").get())
                add("implementation", libs.findLibrary("androidx-room-paging").get())
                add("ksp", libs.findLibrary("androidx-room-compiler").get())
                add("testImplementation", libs.findLibrary("androidx-room-testing").get())
                add("androidTestImplementation", libs.findLibrary("androidx-room-testing").get())
            }
        }
    }
    
    // Extension function to access version catalog
    private val Project.libs
        get() = extensions.getByType(org.gradle.api.artifacts.VersionCatalogsExtension::class.java)
            .named("libs")
}
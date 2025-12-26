import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Convention plugin for Android Library modules with Compose support in SpaceTec project.
 * 
 * Applies common configurations for library modules with Compose including:
 * - Android SDK versions
 * - Compose setup
 * - Kotlin compiler options
 * - Common dependencies
 */
class SpaceTecAndroidLibraryComposePlugin : Plugin<Project> {
    
    override fun apply(target: Project) {
        with(target) {
            // Apply required plugins
            with(pluginManager) {
                apply("spacetec.android.library")
            }
            
            // Configure Android extension
            extensions.configure<LibraryExtension> {
                configureAndroidLibraryCompose(this)
            }
        }
    }
    
    private fun Project.configureAndroidLibraryCompose(extension: LibraryExtension) {
        extension.apply {
            buildFeatures {
                compose = SpaceTecBuildConfig.BuildFeatures.COMPOSE
            }
            
            composeOptions {
                kotlinCompilerExtensionVersion = SpaceTecBuildConfig.ComposeOptions.KOTLIN_COMPILER_EXTENSION_VERSION
            }
        }
    }
}
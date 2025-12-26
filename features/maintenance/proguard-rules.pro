# Proguard rules for maintenance module
# Keep all public classes and methods that might be used by other modules
-keep public class com.spacetec.features.maintenance.** { *; }

# Keep any classes that extend Android components
-keep class * extends androidx.compose.runtime.Composable
-keep @androidx.compose.runtime.Composable class * { *; }

# Keep view models
-keep class * extends androidx.lifecycle.ViewModel { *; }

# Keep data classes used in the module
-keep class com.spacetec.features.maintenance.** { <init>(...); }

# Keep any interface implementations
-keep class * implements com.spacetec.features.maintenance.**

# Keep classes that might be used by reflection
-keep class com.spacetec.features.maintenance.**.di.** { *; }
-keep class com.spacetec.features.maintenance.**.repository.** { *; }
-keep class com.spacetec.features.maintenance.**.usecase.** { *; }

# Keep Hilt/Dagger related classes
-keep @dagger.hilt.InstallIn class * { *; }
-keep @javax.inject.* class * { *; }
-keep class * implements dagger.hilt.internal.GeneratedComponent{ *; }
-keep class * extends dagger.hilt.internal.GeneratedComponentManager { *; }

# Keep Compose related classes
-keep class * extends androidx.compose.ui.Alignment
-keep @androidx.compose.ui.tooling.preview.Preview class * { *; }

# Keep any classes that might be used in navigation
-keep class * extends androidx.navigation.NavController { *; }
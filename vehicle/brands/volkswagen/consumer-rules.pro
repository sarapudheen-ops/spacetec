# Consumer ProGuard rules for VW Diagnostics module
# These rules will be applied to the consumer of this library

# Keep VW protocol classes
-keep class com.spacetec.vehicle.brands.volkswagen.vw.** { *; }
-keep class com.spacetec.vehicle.brands.volkswagen.elm327.** { *; }
-keep class com.spacetec.vehicle.brands.volkswagen.bluetooth.** { *; }
-keep class com.spacetec.vehicle.brands.volkswagen.service.** { *; }

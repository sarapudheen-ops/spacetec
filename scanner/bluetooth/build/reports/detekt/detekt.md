# detekt

## Metrics

* 350 number of properties

* 157 number of functions

* 47 number of classes

* 6 number of packages

* 16 number of kt files

## Complexity Report

* 4,971 lines of code (loc)

* 3,270 source lines of code (sloc)

* 2,303 logical lines of code (lloc)

* 1,064 comment lines of code (cloc)

* 504 cyclomatic complexity (mcc)

* 490 cognitive complexity

* 4 number of total code smells

* 32% comment source ratio

* 218 mcc per 1,000 lloc

* 1 code smells per 1,000 lloc

## Findings (4)

### complexity, CyclomaticComplexMethod (1)

Prefer splitting up complex methods into smaller, easier to test methods.

[Documentation](https://detekt.dev/docs/rules/complexity#cyclomaticcomplexmethod)

* /home/yungblud/Desktop/SpaceTec/scanner/bluetooth/src/main/java/com/spacetec/scanner/bluetooth/classic/BluetoothClassicConnection.kt:126:26
```
The function doConnect appears to be too complex based on Cyclomatic Complexity (complexity: 15). Defined complexity threshold for methods is set to '15'
```
```kotlin
123     // ═══════════════════════════════════════════════════════════════════════
124 
125     @SuppressLint("MissingPermission")
126     override suspend fun doConnect(
!!!                          ^ error
127         address: String,
128         config: ConnectionConfig
129     ): ConnectionInfo = withContext(dispatcher) {

```

### style, NewLineAtEndOfFile (3)

Checks whether files end with a line separator.

[Documentation](https://detekt.dev/docs/rules/style#newlineatendoffile)

* /home/yungblud/Desktop/SpaceTec/scanner/bluetooth/src/main/java/com/spacetec/scanner/bluetooth/BluetoothDiscovery.kt:588:2
```
The file /home/yungblud/Desktop/SpaceTec/scanner/bluetooth/src/main/java/com/spacetec/scanner/bluetooth/BluetoothDiscovery.kt is not ending with a new line.
```
```kotlin
585             return context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
586         }
587     }
588 }
!!!  ^ error

```

* /home/yungblud/Desktop/SpaceTec/scanner/bluetooth/src/main/java/com/spacetec/scanner/bluetooth/ble/BluetoothLEConnection.kt:632:2
```
The file /home/yungblud/Desktop/SpaceTec/scanner/bluetooth/src/main/java/com/spacetec/scanner/bluetooth/ble/BluetoothLEConnection.kt is not ending with a new line.
```
```kotlin
629             return context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
630         }
631     }
632 }
!!!  ^ error

```

* /home/yungblud/Desktop/SpaceTec/scanner/bluetooth/src/main/java/com/spacetec/scanner/bluetooth/classic/BluetoothClassicConnection.kt:634:2
```
The file /home/yungblud/Desktop/SpaceTec/scanner/bluetooth/src/main/java/com/spacetec/scanner/bluetooth/classic/BluetoothClassicConnection.kt is not ending with a new line.
```
```kotlin
631             return adapter?.isEnabled == true
632         }
633     }
634 }
!!!  ^ error

```

generated with [detekt version 1.23.4](https://detekt.dev/) on 2025-12-25 16:12:10 UTC

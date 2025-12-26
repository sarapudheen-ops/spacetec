# detekt

## Metrics

* 845 number of properties

* 366 number of functions

* 264 number of classes

* 22 number of packages

* 74 number of kt files

## Complexity Report

* 11,878 lines of code (loc)

* 9,667 source lines of code (sloc)

* 7,331 logical lines of code (lloc)

* 998 comment lines of code (cloc)

* 1,434 cyclomatic complexity (mcc)

* 1,265 cognitive complexity

* 11 number of total code smells

* 10% comment source ratio

* 195 mcc per 1,000 lloc

* 1 code smells per 1,000 lloc

## Findings (11)

### exceptions, SwallowedException (4)

The caught exception is swallowed. The original exception could be lost.

[Documentation](https://detekt.dev/docs/rules/exceptions#swallowedexception)

* /home/yungblud/Desktop/SpaceTec/app/src/main/java/com/obdreader/data/bluetooth/BluetoothManager.kt:112:26
```
The caught exception is swallowed. The original exception could be lost.
```
```kotlin
109             if (hasPermission) {
110                 try {
111                     device.type
112                 } catch (e: SecurityException) {
!!!                          ^ error
113                     BluetoothDevice.DEVICE_TYPE_CLASSIC // Default to classic on permission error
114                 }
115             } else {

```

* /home/yungblud/Desktop/SpaceTec/app/src/main/java/com/obdreader/data/bluetooth/BluetoothManager.kt:121:22
```
The caught exception is swallowed. The original exception could be lost.
```
```kotlin
118         } else {
119             try {
120                 device.type
121             } catch (e: SecurityException) {
!!!                      ^ error
122                 BluetoothDevice.DEVICE_TYPE_CLASSIC // Default to classic on permission error
123             }
124         }

```

* /home/yungblud/Desktop/SpaceTec/app/src/main/java/com/obdreader/data/bluetooth/ble/BLEDevice.kt:63:30
```
The caught exception is swallowed. The original exception could be lost.
```
```kotlin
60                 if (hasPermission) {
61                     try {
62                         result.device.name
63                     } catch (e: SecurityException) {
!!                              ^ error
64                         null
65                     }
66                 } else {

```

* /home/yungblud/Desktop/SpaceTec/app/src/main/java/com/obdreader/data/bluetooth/ble/BLEDevice.kt:72:26
```
The caught exception is swallowed. The original exception could be lost.
```
```kotlin
69             } else {
70                 try {
71                     result.device.name
72                 } catch (e: SecurityException) {
!!                          ^ error
73                     null
74                 }
75             }

```

### style, MaxLineLength (1)

Line detected, which is longer than the defined maximum line length in the code style.

[Documentation](https://detekt.dev/docs/rules/style#maxlinelength)

* /home/yungblud/Desktop/SpaceTec/app/src/main/java/com/obdreader/data/obd/protocol/ELM327Protocol.kt:110:1
```
Line detected, which is longer than the defined maximum line length in the code style.
```
```kotlin
107             isInitialized.set(true)
108             
109             // At this point, currentProtocol is guaranteed non-null due to the guard at line 97
110             val safeProtocol = currentProtocol ?: throw IllegalStateException("Protocol should have been set but was null")
!!! ^ error
111             Result.success(
112                 AdapterInfo(
113                     version = adapterVersion ?: "Unknown",

```

### style, NewLineAtEndOfFile (5)

Checks whether files end with a line separator.

[Documentation](https://detekt.dev/docs/rules/style#newlineatendoffile)

* /home/yungblud/Desktop/SpaceTec/app/src/main/java/com/obdreader/data/bluetooth/ble/BLEConnection.kt:390:2
```
The file /home/yungblud/Desktop/SpaceTec/app/src/main/java/com/obdreader/data/bluetooth/ble/BLEConnection.kt is not ending with a new line.
```
```kotlin
387      */
388     @SuppressLint("MissingPermission")
389     fun readRssi(): Boolean = gatt?.readRemoteRssi() ?: false
390 }
!!!  ^ error

```

* /home/yungblud/Desktop/SpaceTec/app/src/main/java/com/obdreader/data/obd/protocol/ELM327Protocol.kt:366:2
```
The file /home/yungblud/Desktop/SpaceTec/app/src/main/java/com/obdreader/data/obd/protocol/ELM327Protocol.kt is not ending with a new line.
```
```kotlin
363         
364         return supported
365     }
366 }
!!!  ^ error

```

* /home/yungblud/Desktop/SpaceTec/app/src/main/kotlin/com/spacetec/obd/ui/screens/ConnectionScreen.kt:193:2
```
The file /home/yungblud/Desktop/SpaceTec/app/src/main/kotlin/com/spacetec/obd/ui/screens/ConnectionScreen.kt is not ending with a new line.
```
```kotlin
190             }
191         }
192     }
193 }
!!!  ^ error

```

* /home/yungblud/Desktop/SpaceTec/app/src/main/kotlin/com/spacetec/obd/ui/screens/ReadinessScreen.kt:357:2
```
The file /home/yungblud/Desktop/SpaceTec/app/src/main/kotlin/com/spacetec/obd/ui/screens/ReadinessScreen.kt is not ending with a new line.
```
```kotlin
354             }
355         }
356     }
357 }
!!!  ^ error

```

* /home/yungblud/Desktop/SpaceTec/app/src/main/kotlin/com/spacetec/obd/ui/screens/SettingsScreen.kt:244:2
```
The file /home/yungblud/Desktop/SpaceTec/app/src/main/kotlin/com/spacetec/obd/ui/screens/SettingsScreen.kt is not ending with a new line.
```
```kotlin
241             Icon(Icons.Default.ChevronRight, null, tint = Color.Gray)
242         }
243     }
244 }
!!!  ^ error

```

### style, UseCheckOrError (1)

Use check() or error() instead of throwing an IllegalStateException.

[Documentation](https://detekt.dev/docs/rules/style#usecheckorerror)

* /home/yungblud/Desktop/SpaceTec/app/src/main/java/com/obdreader/data/obd/protocol/ELM327Protocol.kt:110:51
```
Use check() or error() instead of throwing an IllegalStateException.
```
```kotlin
107             isInitialized.set(true)
108             
109             // At this point, currentProtocol is guaranteed non-null due to the guard at line 97
110             val safeProtocol = currentProtocol ?: throw IllegalStateException("Protocol should have been set but was null")
!!!                                                   ^ error
111             Result.success(
112                 AdapterInfo(
113                     version = adapterVersion ?: "Unknown",

```

generated with [detekt version 1.23.4](https://detekt.dev/) on 2025-12-25 22:52:47 UTC

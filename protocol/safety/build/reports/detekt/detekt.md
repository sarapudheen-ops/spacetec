# detekt

## Metrics

* 7 number of properties

* 12 number of functions

* 25 number of classes

* 1 number of packages

* 1 number of kt files

## Complexity Report

* 508 lines of code (loc)

* 365 source lines of code (sloc)

* 268 logical lines of code (lloc)

* 120 comment lines of code (cloc)

* 56 cyclomatic complexity (mcc)

* 42 cognitive complexity

* 15 number of total code smells

* 32% comment source ratio

* 208 mcc per 1,000 lloc

* 55 code smells per 1,000 lloc

## Findings (15)

### complexity, TooManyFunctions (1)

Too many functions inside a/an file/class/object/interface always indicate a violation of the single responsibility principle. Maybe the file/class/object/interface wants to manage too many things at once. Extract functionality which clearly belongs together.

[Documentation](https://detekt.dev/docs/rules/complexity#toomanyfunctions)

* /home/yungblud/Desktop/SpaceTec/protocol/safety/src/main/java/com/spacetec/protocol/safety/SafetyManager.kt:12:7
```
Class 'SafetyManager' with '12' functions detected. Defined threshold inside classes is set to '11'
```
```kotlin
9   * SafetyManager handles safety-critical operations for automotive diagnostic protocols.
10  * This includes checks for voltage, engine status, transmission position, etc.
11  */
12 class SafetyManager {
!!       ^ error
13     
14     /**
15      * Represents the safety status of the vehicle before performing critical operations

```

### exceptions, SwallowedException (1)

The caught exception is swallowed. The original exception could be lost.

[Documentation](https://detekt.dev/docs/rules/exceptions#swallowedexception)

* /home/yungblud/Desktop/SpaceTec/protocol/safety/src/main/java/com/spacetec/protocol/safety/SafetyManager.kt:375:18
```
The caught exception is swallowed. The original exception could be lost.
```
```kotlin
372                 brakeApplied = true,    // Would require brake switch reading
373                 temperature = 20.0      // Would be read from ambient temperature sensor
374             )
375         } catch (e: Exception) {
!!!                  ^ error
376             // If we can't read actual values, return safe defaults
377             return VehicleStatus(
378                 engineRunning = false,

```

### exceptions, TooGenericExceptionCaught (1)

The caught exception is too generic. Prefer catching specific exceptions to the case that is currently handled.

[Documentation](https://detekt.dev/docs/rules/exceptions#toogenericexceptioncaught)

* /home/yungblud/Desktop/SpaceTec/protocol/safety/src/main/java/com/spacetec/protocol/safety/SafetyManager.kt:375:18
```
The caught exception is too generic. Prefer catching specific exceptions to the case that is currently handled.
```
```kotlin
372                 brakeApplied = true,    // Would require brake switch reading
373                 temperature = 20.0      // Would be read from ambient temperature sensor
374             )
375         } catch (e: Exception) {
!!!                  ^ error
376             // If we can't read actual values, return safe defaults
377             return VehicleStatus(
378                 engineRunning = false,

```

### style, FunctionOnlyReturningConstant (3)

A function that only returns a constant is misleading. Consider declaring a constant instead.

[Documentation](https://detekt.dev/docs/rules/style#functiononlyreturningconstant)

* /home/yungblud/Desktop/SpaceTec/protocol/safety/src/main/java/com/spacetec/protocol/safety/SafetyManager.kt:410:17
```
parseVoltageFromResponse is returning a constant. Prefer declaring a constant instead.
```
```kotlin
407      * @param response The raw response from ECU
408      * @return Voltage value in volts
409      */
410     private fun parseVoltageFromResponse(response: String): Double {
!!!                 ^ error
411         // Parse the voltage from response (typically bytes 3-4 in response)
412         // Voltage = (A*256 + B) / 1000 where A and B are the voltage bytes
413         // Implementation would depend on exact response format

```

* /home/yungblud/Desktop/SpaceTec/protocol/safety/src/main/java/com/spacetec/protocol/safety/SafetyManager.kt:422:17
```
parseRPMFromResponse is returning a constant. Prefer declaring a constant instead.
```
```kotlin
419      * @param response The raw response from ECU
420      * @return RPM value
421      */
422     private fun parseRPMFromResponse(response: String): Int {
!!!                 ^ error
423         // Parse RPM from response (typically bytes 3-4 in response)
424         // RPM = ((A*256) + B) / 4 where A and B are the RPM bytes
425         return 0 // Placeholder

```

* /home/yungblud/Desktop/SpaceTec/protocol/safety/src/main/java/com/spacetec/protocol/safety/SafetyManager.kt:433:17
```
parseSpeedFromResponse is returning a constant. Prefer declaring a constant instead.
```
```kotlin
430      * @param response The raw response from ECU
431      * @return Speed in km/h
432      */
433     private fun parseSpeedFromResponse(response: String): Double {
!!!                 ^ error
434         // Parse speed from response (typically byte 3 in response)
435         // Speed = A where A is the speed byte
436         return 0.0 // Placeholder

```

### style, ReturnCount (1)

Restrict the number of return statements in methods.

[Documentation](https://detekt.dev/docs/rules/style#returncount)

* /home/yungblud/Desktop/SpaceTec/protocol/safety/src/main/java/com/spacetec/protocol/safety/SafetyManager.kt:335:17
```
Function getActualVehicleStatus has 3 return statements which exceeds the limit of 2.
```
```kotlin
332      * @param connection The scanner connection to read vehicle parameters from
333      * @return VehicleStatus with actual values from the vehicle
334      */
335     suspend fun getActualVehicleStatus(connection: com.spacetec.transport.contract.ScannerConnection?): VehicleStatus {
!!!                 ^ error
336         if (connection == null) {
337             // If no connection, return safe defaults
338             return VehicleStatus(

```

### style, UnusedParameter (4)

Function parameter is unused and should be removed.

[Documentation](https://detekt.dev/docs/rules/style#unusedparameter)

* /home/yungblud/Desktop/SpaceTec/protocol/safety/src/main/java/com/spacetec/protocol/safety/SafetyManager.kt:398:9
```
Function parameter `mode` is unused.
```
```kotlin
395      */
396     private suspend fun readOBDParameter(
397         connection: com.spacetec.transport.contract.ScannerConnection,
398         mode: Int,
!!!         ^ error
399         pid: Int
400     ): String {
401         val command = "01%02X".format(pid) // Format as OBD command

```

* /home/yungblud/Desktop/SpaceTec/protocol/safety/src/main/java/com/spacetec/protocol/safety/SafetyManager.kt:410:42
```
Function parameter `response` is unused.
```
```kotlin
407      * @param response The raw response from ECU
408      * @return Voltage value in volts
409      */
410     private fun parseVoltageFromResponse(response: String): Double {
!!!                                          ^ error
411         // Parse the voltage from response (typically bytes 3-4 in response)
412         // Voltage = (A*256 + B) / 1000 where A and B are the voltage bytes
413         // Implementation would depend on exact response format

```

* /home/yungblud/Desktop/SpaceTec/protocol/safety/src/main/java/com/spacetec/protocol/safety/SafetyManager.kt:422:38
```
Function parameter `response` is unused.
```
```kotlin
419      * @param response The raw response from ECU
420      * @return RPM value
421      */
422     private fun parseRPMFromResponse(response: String): Int {
!!!                                      ^ error
423         // Parse RPM from response (typically bytes 3-4 in response)
424         // RPM = ((A*256) + B) / 4 where A and B are the RPM bytes
425         return 0 // Placeholder

```

* /home/yungblud/Desktop/SpaceTec/protocol/safety/src/main/java/com/spacetec/protocol/safety/SafetyManager.kt:433:40
```
Function parameter `response` is unused.
```
```kotlin
430      * @param response The raw response from ECU
431      * @return Speed in km/h
432      */
433     private fun parseSpeedFromResponse(response: String): Double {
!!!                                        ^ error
434         // Parse speed from response (typically byte 3 in response)
435         // Speed = A where A is the speed byte
436         return 0.0 // Placeholder

```

### style, UnusedPrivateMember (4)

Private function is unused and should be removed.

[Documentation](https://detekt.dev/docs/rules/style#unusedprivatemember)

* /home/yungblud/Desktop/SpaceTec/protocol/safety/src/main/java/com/spacetec/protocol/safety/SafetyManager.kt:396:25
```
Private function `readOBDParameter` is unused.
```
```kotlin
393      * @param pid The Parameter ID to read
394      * @return Raw response from the ECU
395      */
396     private suspend fun readOBDParameter(
!!!                         ^ error
397         connection: com.spacetec.transport.contract.ScannerConnection,
398         mode: Int,
399         pid: Int

```

* /home/yungblud/Desktop/SpaceTec/protocol/safety/src/main/java/com/spacetec/protocol/safety/SafetyManager.kt:410:17
```
Private function `parseVoltageFromResponse` is unused.
```
```kotlin
407      * @param response The raw response from ECU
408      * @return Voltage value in volts
409      */
410     private fun parseVoltageFromResponse(response: String): Double {
!!!                 ^ error
411         // Parse the voltage from response (typically bytes 3-4 in response)
412         // Voltage = (A*256 + B) / 1000 where A and B are the voltage bytes
413         // Implementation would depend on exact response format

```

* /home/yungblud/Desktop/SpaceTec/protocol/safety/src/main/java/com/spacetec/protocol/safety/SafetyManager.kt:422:17
```
Private function `parseRPMFromResponse` is unused.
```
```kotlin
419      * @param response The raw response from ECU
420      * @return RPM value
421      */
422     private fun parseRPMFromResponse(response: String): Int {
!!!                 ^ error
423         // Parse RPM from response (typically bytes 3-4 in response)
424         // RPM = ((A*256) + B) / 4 where A and B are the RPM bytes
425         return 0 // Placeholder

```

* /home/yungblud/Desktop/SpaceTec/protocol/safety/src/main/java/com/spacetec/protocol/safety/SafetyManager.kt:433:17
```
Private function `parseSpeedFromResponse` is unused.
```
```kotlin
430      * @param response The raw response from ECU
431      * @return Speed in km/h
432      */
433     private fun parseSpeedFromResponse(response: String): Double {
!!!                 ^ error
434         // Parse speed from response (typically byte 3 in response)
435         // Speed = A where A is the speed byte
436         return 0.0 // Placeholder

```

generated with [detekt version 1.23.4](https://detekt.dev/) on 2025-12-25 22:23:45 UTC

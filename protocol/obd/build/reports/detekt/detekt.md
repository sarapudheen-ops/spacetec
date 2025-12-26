# detekt

## Metrics

* 236 number of properties

* 172 number of functions

* 40 number of classes

* 6 number of packages

* 21 number of kt files

## Complexity Report

* 3,317 lines of code (loc)

* 1,931 source lines of code (sloc)

* 1,349 logical lines of code (lloc)

* 1,062 comment lines of code (cloc)

* 479 cyclomatic complexity (mcc)

* 333 cognitive complexity

* 2 number of total code smells

* 54% comment source ratio

* 355 mcc per 1,000 lloc

* 1 code smells per 1,000 lloc

## Findings (2)

### complexity, CyclomaticComplexMethod (1)

Prefer splitting up complex methods into smaller, easier to test methods.

[Documentation](https://detekt.dev/docs/rules/complexity#cyclomaticcomplexmethod)

* /home/yungblud/Desktop/SpaceTec/protocol/obd/src/main/kotlin/com/spacetec/automotive/protocol/obd/ObdProtocol.kt:342:17
```
The function readFreezeFrame appears to be too complex based on Cyclomatic Complexity (complexity: 87). Defined complexity threshold for methods is set to '15'
```
```kotlin
339     /**
340      * Reads freeze frame data for a specific frame number.
341      */
342     suspend fun readFreezeFrame(frameNumber: Int = 0): AppResult<FreezeFrame> {
!!!                 ^ error
343         val parameters = mutableListOf<FreezeFrameParameter>()
344         
345         // Read supported PIDs for freeze frame

```

### complexity, NestedBlockDepth (1)

Excessive nesting leads to hidden complexity. Prefer extracting code to make it easier to understand.

[Documentation](https://detekt.dev/docs/rules/complexity#nestedblockdepth)

* /home/yungblud/Desktop/SpaceTec/protocol/obd/src/main/kotlin/com/spacetec/automotive/protocol/obd/ObdProtocol.kt:342:17
```
Function readFreezeFrame is nested too deeply.
```
```kotlin
339     /**
340      * Reads freeze frame data for a specific frame number.
341      */
342     suspend fun readFreezeFrame(frameNumber: Int = 0): AppResult<FreezeFrame> {
!!!                 ^ error
343         val parameters = mutableListOf<FreezeFrameParameter>()
344         
345         // Read supported PIDs for freeze frame

```

generated with [detekt version 1.23.4](https://detekt.dev/) on 2025-12-25 16:12:10 UTC

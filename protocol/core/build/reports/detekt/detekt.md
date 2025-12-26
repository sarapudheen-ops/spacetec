# detekt

## Metrics

* 353 number of properties

* 197 number of functions

* 180 number of classes

* 6 number of packages

* 27 number of kt files

## Complexity Report

* 5,345 lines of code (loc)

* 2,882 source lines of code (sloc)

* 2,433 logical lines of code (lloc)

* 1,789 comment lines of code (cloc)

* 577 cyclomatic complexity (mcc)

* 319 cognitive complexity

* 33 number of total code smells

* 62% comment source ratio

* 237 mcc per 1,000 lloc

* 13 code smells per 1,000 lloc

## Findings (33)

### complexity, CyclomaticComplexMethod (1)

Prefer splitting up complex methods into smaller, easier to test methods.

[Documentation](https://detekt.dev/docs/rules/complexity#cyclomaticcomplexmethod)

* /home/yungblud/Desktop/SpaceTec/protocol/core/src/main/java/com/spacetec/protocol/core/base/BaseProtocol.kt:1552:13
```
The function fromException appears to be too complex based on Cyclomatic Complexity (complexity: 15). Defined complexity threshold for methods is set to '15'
```
```kotlin
1549          * @param operation Optional operation description
1550          * @return ProtocolError instance
1551          */
1552         fun fromException(e: Exception, operation: String? = null): ProtocolError {
!!!!             ^ error
1553             val (code, message) = when (e) {
1554                 is TimeoutException -> CODE_TIMEOUT to "Operation timed out"
1555                 is CommunicationException -> CODE_COMMUNICATION_FAILED to "Communication failed"

```

### complexity, LongParameterList (1)

The more parameters a function has the more complex it is. Long parameter lists are often used to control complex algorithms and violate the Single Responsibility Principle. Prefer functions with short parameter lists.

[Documentation](https://detekt.dev/docs/rules/complexity#longparameterlist)

* /home/yungblud/Desktop/SpaceTec/protocol/core/src/main/java/com/spacetec/protocol/core/base/BaseProtocol.kt:1373:27
```
The constructor(message: String, code: Int, recoverable: Boolean, nrc: Int?, serviceId: Int?, details: Map<String, Any>, cause: Throwable?) has too many parameters. The current threshold is set to 7.
```
```kotlin
1370 /**
1371  * Represents errors that can occur during protocol operations.
1372  */
1373 sealed class ProtocolError(
!!!!                           ^ error
1374     open val message: String,
1375     open val code: Int = 0,
1376     open val recoverable: Boolean = true,

```

### complexity, TooManyFunctions (1)

Too many functions inside a/an file/class/object/interface always indicate a violation of the single responsibility principle. Maybe the file/class/object/interface wants to manage too many things at once. Extract functionality which clearly belongs together.

[Documentation](https://detekt.dev/docs/rules/complexity#toomanyfunctions)

* /home/yungblud/Desktop/SpaceTec/protocol/core/src/main/java/com/spacetec/protocol/core/base/BaseProtocol.kt:120:16
```
Class 'BaseProtocol' with '32' functions detected. Defined threshold inside classes is set to '11'
```
```kotlin
117  * @see ProtocolConfig
118  * @see DiagnosticMessage
119  */
120 abstract class BaseProtocol : Protocol {
!!!                ^ error
121 
122     // ==================== Abstract Properties ====================
123 

```

### empty-blocks, EmptyClassBlock (1)

Empty block of code detected. As they serve no purpose they should be removed.

[Documentation](https://detekt.dev/docs/rules/empty-blocks#emptyclassblock)

* /home/yungblud/Desktop/SpaceTec/protocol/core/src/main/java/com/spacetec/protocol/core/base/BaseProtocol.kt:1072:28
```
The class or object ProtocolState is empty.
```
```kotlin
1069  *                                Error
1070  * ```
1071  */
1072 sealed class ProtocolState {
!!!!                            ^ error
1073     
1074 
1075 }

```

### style, MagicNumber (24)

Report magic numbers. Magic number is a numeric literal that is not defined as a constant and hence it's unclear what the purpose of this number is. It's better to declare such numbers as constants and give them a proper name. By default, -1, 0, 1, and 2 are not considered to be magic numbers.

[Documentation](https://detekt.dev/docs/rules/style#magicnumber)

* /home/yungblud/Desktop/SpaceTec/protocol/core/src/main/java/com/spacetec/protocol/core/base/BaseProtocol.kt:800:23
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
797         keepAliveJob?.cancel()
798         keepAliveJob = protocolScope.launch {
799             while (isActive && isSessionActive && !isShuttingDown.get()) {
800                 delay(1000L)
!!!                       ^ error
801                 if (isSessionActive) {
802                     try {
803                         sendKeepAlive()

```

* /home/yungblud/Desktop/SpaceTec/protocol/core/src/main/java/com/spacetec/protocol/core/base/BaseProtocol.kt:1412:50
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
1409         append("ProtocolError[")
1410         append("code=$code")
1411         append(", category=$category")
1412         nrc?.let { append(", nrc=0x${it.toString(16).uppercase()}") }
!!!!                                                  ^ error
1413         serviceId?.let { append(", service=0x${it.toString(16).uppercase()}") }
1414         append(", recoverable=$recoverable")
1415         append("]: $message")

```

* /home/yungblud/Desktop/SpaceTec/protocol/core/src/main/java/com/spacetec/protocol/core/base/BaseProtocol.kt:1413:60
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
1410         append("code=$code")
1411         append(", category=$category")
1412         nrc?.let { append(", nrc=0x${it.toString(16).uppercase()}") }
1413         serviceId?.let { append(", service=0x${it.toString(16).uppercase()}") }
!!!!                                                            ^ error
1414         append(", recoverable=$recoverable")
1415         append("]: $message")
1416         nrcDescription?.let { append(" ($it)") }

```

* /home/yungblud/Desktop/SpaceTec/protocol/core/src/main/java/com/spacetec/protocol/core/base/BaseProtocol.kt:1505:23
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
1502     
1503     /** Error category enumeration. */
1504     enum class ErrorCategory(val range: IntRange, val description: String) {
1505         COMMUNICATION(1000..1999, "Communication Error"),
!!!!                       ^ error
1506         PROTOCOL(2000..2999, "Protocol Error"),
1507         NRC(3000..3999, "Negative Response"),
1508         TIMEOUT(4000..4999, "Timeout Error"),

```

* /home/yungblud/Desktop/SpaceTec/protocol/core/src/main/java/com/spacetec/protocol/core/base/BaseProtocol.kt:1505:29
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
1502     
1503     /** Error category enumeration. */
1504     enum class ErrorCategory(val range: IntRange, val description: String) {
1505         COMMUNICATION(1000..1999, "Communication Error"),
!!!!                             ^ error
1506         PROTOCOL(2000..2999, "Protocol Error"),
1507         NRC(3000..3999, "Negative Response"),
1508         TIMEOUT(4000..4999, "Timeout Error"),

```

* /home/yungblud/Desktop/SpaceTec/protocol/core/src/main/java/com/spacetec/protocol/core/base/BaseProtocol.kt:1506:18
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
1503     /** Error category enumeration. */
1504     enum class ErrorCategory(val range: IntRange, val description: String) {
1505         COMMUNICATION(1000..1999, "Communication Error"),
1506         PROTOCOL(2000..2999, "Protocol Error"),
!!!!                  ^ error
1507         NRC(3000..3999, "Negative Response"),
1508         TIMEOUT(4000..4999, "Timeout Error"),
1509         SECURITY(5000..5999, "Security Error"),

```

* /home/yungblud/Desktop/SpaceTec/protocol/core/src/main/java/com/spacetec/protocol/core/base/BaseProtocol.kt:1506:24
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
1503     /** Error category enumeration. */
1504     enum class ErrorCategory(val range: IntRange, val description: String) {
1505         COMMUNICATION(1000..1999, "Communication Error"),
1506         PROTOCOL(2000..2999, "Protocol Error"),
!!!!                        ^ error
1507         NRC(3000..3999, "Negative Response"),
1508         TIMEOUT(4000..4999, "Timeout Error"),
1509         SECURITY(5000..5999, "Security Error"),

```

* /home/yungblud/Desktop/SpaceTec/protocol/core/src/main/java/com/spacetec/protocol/core/base/BaseProtocol.kt:1507:13
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
1504     enum class ErrorCategory(val range: IntRange, val description: String) {
1505         COMMUNICATION(1000..1999, "Communication Error"),
1506         PROTOCOL(2000..2999, "Protocol Error"),
1507         NRC(3000..3999, "Negative Response"),
!!!!             ^ error
1508         TIMEOUT(4000..4999, "Timeout Error"),
1509         SECURITY(5000..5999, "Security Error"),
1510         STATE(6000..6999, "State Error"),

```

* /home/yungblud/Desktop/SpaceTec/protocol/core/src/main/java/com/spacetec/protocol/core/base/BaseProtocol.kt:1507:19
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
1504     enum class ErrorCategory(val range: IntRange, val description: String) {
1505         COMMUNICATION(1000..1999, "Communication Error"),
1506         PROTOCOL(2000..2999, "Protocol Error"),
1507         NRC(3000..3999, "Negative Response"),
!!!!                   ^ error
1508         TIMEOUT(4000..4999, "Timeout Error"),
1509         SECURITY(5000..5999, "Security Error"),
1510         STATE(6000..6999, "State Error"),

```

* /home/yungblud/Desktop/SpaceTec/protocol/core/src/main/java/com/spacetec/protocol/core/base/BaseProtocol.kt:1508:17
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
1505         COMMUNICATION(1000..1999, "Communication Error"),
1506         PROTOCOL(2000..2999, "Protocol Error"),
1507         NRC(3000..3999, "Negative Response"),
1508         TIMEOUT(4000..4999, "Timeout Error"),
!!!!                 ^ error
1509         SECURITY(5000..5999, "Security Error"),
1510         STATE(6000..6999, "State Error"),
1511         CONFIGURATION(7000..7999, "Configuration Error"),

```

* /home/yungblud/Desktop/SpaceTec/protocol/core/src/main/java/com/spacetec/protocol/core/base/BaseProtocol.kt:1508:23
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
1505         COMMUNICATION(1000..1999, "Communication Error"),
1506         PROTOCOL(2000..2999, "Protocol Error"),
1507         NRC(3000..3999, "Negative Response"),
1508         TIMEOUT(4000..4999, "Timeout Error"),
!!!!                       ^ error
1509         SECURITY(5000..5999, "Security Error"),
1510         STATE(6000..6999, "State Error"),
1511         CONFIGURATION(7000..7999, "Configuration Error"),

```

* /home/yungblud/Desktop/SpaceTec/protocol/core/src/main/java/com/spacetec/protocol/core/base/BaseProtocol.kt:1509:18
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
1506         PROTOCOL(2000..2999, "Protocol Error"),
1507         NRC(3000..3999, "Negative Response"),
1508         TIMEOUT(4000..4999, "Timeout Error"),
1509         SECURITY(5000..5999, "Security Error"),
!!!!                  ^ error
1510         STATE(6000..6999, "State Error"),
1511         CONFIGURATION(7000..7999, "Configuration Error"),
1512         INTERNAL(8000..8999, "Internal Error"),

```

* /home/yungblud/Desktop/SpaceTec/protocol/core/src/main/java/com/spacetec/protocol/core/base/BaseProtocol.kt:1509:24
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
1506         PROTOCOL(2000..2999, "Protocol Error"),
1507         NRC(3000..3999, "Negative Response"),
1508         TIMEOUT(4000..4999, "Timeout Error"),
1509         SECURITY(5000..5999, "Security Error"),
!!!!                        ^ error
1510         STATE(6000..6999, "State Error"),
1511         CONFIGURATION(7000..7999, "Configuration Error"),
1512         INTERNAL(8000..8999, "Internal Error"),

```

* /home/yungblud/Desktop/SpaceTec/protocol/core/src/main/java/com/spacetec/protocol/core/base/BaseProtocol.kt:1510:15
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
1507         NRC(3000..3999, "Negative Response"),
1508         TIMEOUT(4000..4999, "Timeout Error"),
1509         SECURITY(5000..5999, "Security Error"),
1510         STATE(6000..6999, "State Error"),
!!!!               ^ error
1511         CONFIGURATION(7000..7999, "Configuration Error"),
1512         INTERNAL(8000..8999, "Internal Error"),
1513         UNKNOWN(9000..9999, "Unknown Error");

```

* /home/yungblud/Desktop/SpaceTec/protocol/core/src/main/java/com/spacetec/protocol/core/base/BaseProtocol.kt:1510:21
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
1507         NRC(3000..3999, "Negative Response"),
1508         TIMEOUT(4000..4999, "Timeout Error"),
1509         SECURITY(5000..5999, "Security Error"),
1510         STATE(6000..6999, "State Error"),
!!!!                     ^ error
1511         CONFIGURATION(7000..7999, "Configuration Error"),
1512         INTERNAL(8000..8999, "Internal Error"),
1513         UNKNOWN(9000..9999, "Unknown Error");

```

* /home/yungblud/Desktop/SpaceTec/protocol/core/src/main/java/com/spacetec/protocol/core/base/BaseProtocol.kt:1511:23
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
1508         TIMEOUT(4000..4999, "Timeout Error"),
1509         SECURITY(5000..5999, "Security Error"),
1510         STATE(6000..6999, "State Error"),
1511         CONFIGURATION(7000..7999, "Configuration Error"),
!!!!                       ^ error
1512         INTERNAL(8000..8999, "Internal Error"),
1513         UNKNOWN(9000..9999, "Unknown Error");
1514         

```

* /home/yungblud/Desktop/SpaceTec/protocol/core/src/main/java/com/spacetec/protocol/core/base/BaseProtocol.kt:1511:29
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
1508         TIMEOUT(4000..4999, "Timeout Error"),
1509         SECURITY(5000..5999, "Security Error"),
1510         STATE(6000..6999, "State Error"),
1511         CONFIGURATION(7000..7999, "Configuration Error"),
!!!!                             ^ error
1512         INTERNAL(8000..8999, "Internal Error"),
1513         UNKNOWN(9000..9999, "Unknown Error");
1514         

```

* /home/yungblud/Desktop/SpaceTec/protocol/core/src/main/java/com/spacetec/protocol/core/base/BaseProtocol.kt:1512:18
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
1509         SECURITY(5000..5999, "Security Error"),
1510         STATE(6000..6999, "State Error"),
1511         CONFIGURATION(7000..7999, "Configuration Error"),
1512         INTERNAL(8000..8999, "Internal Error"),
!!!!                  ^ error
1513         UNKNOWN(9000..9999, "Unknown Error");
1514         
1515         companion object {

```

* /home/yungblud/Desktop/SpaceTec/protocol/core/src/main/java/com/spacetec/protocol/core/base/BaseProtocol.kt:1512:24
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
1509         SECURITY(5000..5999, "Security Error"),
1510         STATE(6000..6999, "State Error"),
1511         CONFIGURATION(7000..7999, "Configuration Error"),
1512         INTERNAL(8000..8999, "Internal Error"),
!!!!                        ^ error
1513         UNKNOWN(9000..9999, "Unknown Error");
1514         
1515         companion object {

```

* /home/yungblud/Desktop/SpaceTec/protocol/core/src/main/java/com/spacetec/protocol/core/base/BaseProtocol.kt:1513:17
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
1510         STATE(6000..6999, "State Error"),
1511         CONFIGURATION(7000..7999, "Configuration Error"),
1512         INTERNAL(8000..8999, "Internal Error"),
1513         UNKNOWN(9000..9999, "Unknown Error");
!!!!                 ^ error
1514         
1515         companion object {
1516             fun fromCode(code: Int): ErrorCategory =

```

* /home/yungblud/Desktop/SpaceTec/protocol/core/src/main/java/com/spacetec/protocol/core/base/BaseProtocol.kt:1513:23
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
1510         STATE(6000..6999, "State Error"),
1511         CONFIGURATION(7000..7999, "Configuration Error"),
1512         INTERNAL(8000..8999, "Internal Error"),
1513         UNKNOWN(9000..9999, "Unknown Error");
!!!!                       ^ error
1514         
1515         companion object {
1516             fun fromCode(code: Int): ErrorCategory =

```

* /home/yungblud/Desktop/SpaceTec/protocol/core/src/main/java/com/spacetec/protocol/core/base/BaseProtocol.kt:1588:59
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
1585             }
1586 
1587             return NegativeResponse(
1588                 message = "Service 0x${serviceId.toString(16).uppercase()} rejected: $description",
!!!!                                                           ^ error
1589                 serviceIdValue = serviceId,
1590                 nrcValue = nrc,
1591                 recoverable = recoverable,

```

* /home/yungblud/Desktop/SpaceTec/protocol/core/src/main/java/com/spacetec/protocol/core/frame/FirstFrame.kt:50:32
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
47             val totalLength = (upperLength shl 8) or lowerLength
48             
49             // Validate total length is reasonable (not zero or excessively large)
50             if (totalLength <= 7) return null // First frames are for multi-frame messages only
!!                                ^ error
51             if (totalLength > 4095) return null // Max ISO-TP length is 4095 bytes
52             
53             // Ensure we have at least 6 bytes of data after PCI and length

```

* /home/yungblud/Desktop/SpaceTec/protocol/core/src/main/java/com/spacetec/protocol/core/frame/FirstFrame.kt:51:31
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
48             
49             // Validate total length is reasonable (not zero or excessively large)
50             if (totalLength <= 7) return null // First frames are for multi-frame messages only
51             if (totalLength > 4095) return null // Max ISO-TP length is 4095 bytes
!!                               ^ error
52             
53             // Ensure we have at least 6 bytes of data after PCI and length
54             if (raw.size < 8) return null

```

### style, MaxLineLength (3)

Line detected, which is longer than the defined maximum line length in the code style.

[Documentation](https://detekt.dev/docs/rules/style#maxlinelength)

* /home/yungblud/Desktop/SpaceTec/protocol/core/src/main/java/com/spacetec/protocol/core/base/BaseProtocol.kt:1560:1
```
Line detected, which is longer than the defined maximum line length in the code style.
```
```kotlin
1557                 else -> CODE_INTERNAL_ERROR to "Internal error occurred"
1558             }
1559 
1560             val fullMessage = operation?.let { "$it: ${message ?: e.message}" } ?: (message ?: e.message ?: "Unknown error")
!!!! ^ error
1561             val details = mapOf("operation" to (operation ?: "unknown"))
1562 
1563             return when (e) {

```

* /home/yungblud/Desktop/SpaceTec/protocol/core/src/main/java/com/spacetec/protocol/core/base/BaseProtocol.kt:1565:1
```
Line detected, which is longer than the defined maximum line length in the code style.
```
```kotlin
1562 
1563             return when (e) {
1564                 is TimeoutException -> Timeout(fullMessage, recoverable = true, details = details, cause = e)
1565                 is CommunicationException -> CommunicationError(fullMessage, recoverable = true, details = details, cause = e)
!!!! ^ error
1566                 is ProtocolException -> ProtocolViolation(fullMessage, recoverable = false, details = details, cause = e)
1567                 else -> ProtocolViolation(fullMessage, recoverable = true, details = details, cause = e)
1568             }

```

* /home/yungblud/Desktop/SpaceTec/protocol/core/src/main/java/com/spacetec/protocol/core/base/BaseProtocol.kt:1566:1
```
Line detected, which is longer than the defined maximum line length in the code style.
```
```kotlin
1563             return when (e) {
1564                 is TimeoutException -> Timeout(fullMessage, recoverable = true, details = details, cause = e)
1565                 is CommunicationException -> CommunicationError(fullMessage, recoverable = true, details = details, cause = e)
1566                 is ProtocolException -> ProtocolViolation(fullMessage, recoverable = false, details = details, cause = e)
!!!! ^ error
1567                 else -> ProtocolViolation(fullMessage, recoverable = true, details = details, cause = e)
1568             }
1569         }

```

### style, NewLineAtEndOfFile (1)

Checks whether files end with a line separator.

[Documentation](https://detekt.dev/docs/rules/style#newlineatendoffile)

* /home/yungblud/Desktop/SpaceTec/protocol/core/src/main/java/com/spacetec/protocol/core/ProtocolManager.kt:1195:2
```
The file /home/yungblud/Desktop/SpaceTec/protocol/core/src/main/java/com/spacetec/protocol/core/ProtocolManager.kt is not ending with a new line.
```
```kotlin
1192      * Algorithm name for logging.
1193      */
1194     val name: String
1195 }
!!!!  ^ error

```

### style, UnusedPrivateMember (1)

Private function is unused and should be removed.

[Documentation](https://detekt.dev/docs/rules/style#unusedprivatemember)

* /home/yungblud/Desktop/SpaceTec/protocol/core/src/main/java/com/spacetec/protocol/core/ProtocolDetector.kt:680:25
```
Private function `testKWP2000SlowInit` is unused.
```
```kotlin
677     /**
678      * Tests KWP2000 with 5-baud slow initialization.
679      */
680     private suspend fun testKWP2000SlowInit(
!!!                         ^ error
681         connection: ScannerConnection,
682         config: DetectionConfig
683     ): Boolean {

```

generated with [detekt version 1.23.4](https://detekt.dev/) on 2025-12-25 23:00:17 UTC

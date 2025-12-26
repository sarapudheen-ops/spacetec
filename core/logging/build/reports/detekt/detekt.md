# detekt

## Metrics

* 239 number of properties

* 119 number of functions

* 56 number of classes

* 2 number of packages

* 9 number of kt files

## Complexity Report

* 2,710 lines of code (loc)

* 1,942 source lines of code (sloc)

* 1,608 logical lines of code (lloc)

* 430 comment lines of code (cloc)

* 389 cyclomatic complexity (mcc)

* 327 cognitive complexity

* 4 number of total code smells

* 22% comment source ratio

* 241 mcc per 1,000 lloc

* 2 code smells per 1,000 lloc

## Findings (4)

### complexity, LongMethod (2)

One method should have one responsibility. Long methods tend to handle many things at once. Prefer smaller methods to make them easier to understand.

[Documentation](https://detekt.dev/docs/rules/complexity#longmethod)

* /home/yungblud/Desktop/SpaceTec/core/logging/src/test/java/com/spacetec/core/logging/ComprehensiveLoggingPropertyTest.kt:147:9
```
The function Property 13 - Diagnostic mode should capture raw communication data with complete metadata is too long (60). The maximum length is 60.
```
```kotlin
144     }
145     
146     @Test
147     fun `Property 13 - Diagnostic mode should capture raw communication data with complete metadata`() = runTest {
!!!         ^ error
148         // Given: A diagnostic logger in raw communication mode
149         val baseLogger = SpaceTecLogger("DiagnosticTest")
150         val diagnosticConfig = DiagnosticLoggerConfig(

```

* /home/yungblud/Desktop/SpaceTec/core/logging/src/test/java/com/spacetec/core/logging/LogManagementPropertyTest.kt:99:9
```
The function Property 14 - Log export should provide complete data in multiple formats is too long (60). The maximum length is 60.
```
```kotlin
96      }
97      
98      @Test
99      fun `Property 14 - Log export should provide complete data in multiple formats`() = runBlocking {
!!          ^ error
100         // Given: A temporary directory with log files
101         val tempDir = Files.createTempDirectory("spacetec_export_test").toFile()
102         val exportDir = Files.createTempDirectory("spacetec_export_output").toFile()

```

### style, MaxLineLength (1)

Line detected, which is longer than the defined maximum line length in the code style.

[Documentation](https://detekt.dev/docs/rules/style#maxlinelength)

* /home/yungblud/Desktop/SpaceTec/core/logging/src/main/java/com/spacetec/core/logging/DiagnosticLogger.kt:53:1
```
Line detected, which is longer than the defined maximum line length in the code style.
```
```kotlin
50 /**
51  * Log error message
52  */
53 suspend fun DiagnosticLogger.error(tag: String, message: String, throwable: Throwable? = null, context: Map<String, Any?> = emptyMap()) {
!! ^ error
54     logError(message, throwable, context + ("tag" to tag))
55 }
56 

```

### style, NewLineAtEndOfFile (1)

Checks whether files end with a line separator.

[Documentation](https://detekt.dev/docs/rules/style#newlineatendoffile)

* /home/yungblud/Desktop/SpaceTec/core/logging/src/main/java/com/spacetec/core/logging/SpaceTecLogger.kt:131:2
```
The file /home/yungblud/Desktop/SpaceTec/core/logging/src/main/java/com/spacetec/core/logging/SpaceTecLogger.kt is not ending with a new line.
```
```kotlin
128     fun exportLogs(): String = logBuffer.joinToString("\n") { it.toFormattedString() }
129 
130     fun clearLogs() = logBuffer.clear()
131 }
!!!  ^ error

```

generated with [detekt version 1.23.4](https://detekt.dev/) on 2025-12-25 22:54:58 UTC

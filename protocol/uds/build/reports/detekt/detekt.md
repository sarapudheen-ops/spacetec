# detekt

## Metrics

* 177 number of properties

* 56 number of functions

* 14 number of classes

* 2 number of packages

* 6 number of kt files

## Complexity Report

* 1,273 lines of code (loc)

* 929 source lines of code (sloc)

* 694 logical lines of code (lloc)

* 246 comment lines of code (cloc)

* 182 cyclomatic complexity (mcc)

* 158 cognitive complexity

* 85 number of total code smells

* 26% comment source ratio

* 262 mcc per 1,000 lloc

* 122 code smells per 1,000 lloc

## Findings (85)

### exceptions, TooGenericExceptionCaught (10)

The caught exception is too generic. Prefer catching specific exceptions to the case that is currently handled.

[Documentation](https://detekt.dev/docs/rules/exceptions#toogenericexceptioncaught)

* /home/yungblud/Desktop/SpaceTec/protocol/uds/src/main/java/com/spacetec/protocol/uds/services/DiagnosticSessionControlService.kt:46:18
```
The caught exception is too generic. Prefer catching specific exceptions to the case that is currently handled.
```
```kotlin
43                     ProtocolError.CommunicationError("Failed to enter session: ${result.exception?.message}")
44                 ))
45             }
46         } catch (e: Exception) {
!!                  ^ error
47             emit(UDSResult.Error(ProtocolError.CommunicationError("Session control error: ${e.message}")))
48         }
49     }

```

* /home/yungblud/Desktop/SpaceTec/protocol/uds/src/main/java/com/spacetec/protocol/uds/services/DiagnosticSessionControlService.kt:87:18
```
The caught exception is too generic. Prefer catching specific exceptions to the case that is currently handled.
```
```kotlin
84             } else {
85                 Result.failure(response.exceptionOrNull() ?: Exception("Unknown error"))
86             }
87         } catch (e: Exception) {
!!                  ^ error
88             Result.failure(e)
89         }
90     }

```

* /home/yungblud/Desktop/SpaceTec/protocol/uds/src/main/java/com/spacetec/protocol/uds/services/ECUResetService.kt:36:18
```
The caught exception is too generic. Prefer catching specific exceptions to the case that is currently handled.
```
```kotlin
33                     ProtocolError.CommunicationError("ECU reset failed: ${result.exceptionOrNull()?.message}")
34                 ))
35             }
36         } catch (e: Exception) {
!!                  ^ error
37             emit(UDSResult.Error(ProtocolError.CommunicationError("ECU reset error: ${e.message}")))
38         }
39     }

```

* /home/yungblud/Desktop/SpaceTec/protocol/uds/src/main/java/com/spacetec/protocol/uds/services/ECUResetService.kt:68:18
```
The caught exception is too generic. Prefer catching specific exceptions to the case that is currently handled.
```
```kotlin
65             } else {
66                 Result.failure(response.exceptionOrNull() ?: Exception("Unknown error"))
67             }
68         } catch (e: Exception) {
!!                  ^ error
69             Result.failure(e)
70         }
71     }

```

* /home/yungblud/Desktop/SpaceTec/protocol/uds/src/main/java/com/spacetec/protocol/uds/services/ReadDTCInformationService.kt:35:18
```
The caught exception is too generic. Prefer catching specific exceptions to the case that is currently handled.
```
```kotlin
32                     ProtocolError.CommunicationError("Read DTC information failed: ${result.exceptionOrNull()?.message}")
33                 ))
34             }
35         } catch (e: Exception) {
!!                  ^ error
36             emit(UDSResult.Error(ProtocolError.CommunicationError("Read DTC information error: ${e.message}")))
37         }
38     }

```

* /home/yungblud/Desktop/SpaceTec/protocol/uds/src/main/java/com/spacetec/protocol/uds/services/ReadDTCInformationService.kt:68:18
```
The caught exception is too generic. Prefer catching specific exceptions to the case that is currently handled.
```
```kotlin
65             } else {
66                 Result.failure(response.exceptionOrNull() ?: Exception("Unknown error"))
67             }
68         } catch (e: Exception) {
!!                  ^ error
69             Result.failure(e)
70         }
71     }

```

* /home/yungblud/Desktop/SpaceTec/protocol/uds/src/main/java/com/spacetec/protocol/uds/services/ReadDataByIdentifierService.kt:35:18
```
The caught exception is too generic. Prefer catching specific exceptions to the case that is currently handled.
```
```kotlin
32                     ProtocolError.CommunicationError("Read data by identifier failed: ${result.exceptionOrNull()?.message}")
33                 ))
34             }
35         } catch (e: Exception) {
!!                  ^ error
36             emit(UDSResult.Error(ProtocolError.CommunicationError("Read data by identifier error: ${e.message}")))
37         }
38     }

```

* /home/yungblud/Desktop/SpaceTec/protocol/uds/src/main/java/com/spacetec/protocol/uds/services/ReadDataByIdentifierService.kt:76:18
```
The caught exception is too generic. Prefer catching specific exceptions to the case that is currently handled.
```
```kotlin
73             } else {
74                 Result.failure(response.exceptionOrNull() ?: Exception("Unknown error"))
75             }
76         } catch (e: Exception) {
!!                  ^ error
77             Result.failure(e)
78         }
79     }

```

* /home/yungblud/Desktop/SpaceTec/protocol/uds/src/main/java/com/spacetec/protocol/uds/services/SecurityAccessService.kt:37:18
```
The caught exception is too generic. Prefer catching specific exceptions to the case that is currently handled.
```
```kotlin
34                     ProtocolError.CommunicationError("Security access failed: ${result.exceptionOrNull()?.message}")
35                 ))
36             }
37         } catch (e: Exception) {
!!                  ^ error
38             emit(UDSResult.Error(ProtocolError.CommunicationError("Security access error: ${e.message}")))
39         }
40     }

```

* /home/yungblud/Desktop/SpaceTec/protocol/uds/src/main/java/com/spacetec/protocol/uds/services/SecurityAccessService.kt:87:18
```
The caught exception is too generic. Prefer catching specific exceptions to the case that is currently handled.
```
```kotlin
84             } else {
85                 Result.failure(response.exceptionOrNull() ?: Exception("Unknown error"))
86             }
87         } catch (e: Exception) {
!!                  ^ error
88             Result.failure(e)
89         }
90     }

```

### potential-bugs, ImplicitDefaultLocale (1)

Implicit default locale used for string processing. Consider using explicit locale.

[Documentation](https://detekt.dev/docs/rules/potential-bugs#implicitdefaultlocale)

* /home/yungblud/Desktop/SpaceTec/protocol/uds/src/main/java/com/spacetec/protocol/uds/services/ReadDTCInformationService.kt:127:28
```
String.format("%c%02X%02X", dtcType, dtcByte1 and 0x3F, dtcByte2) uses implicitly default locale for string formatting.
```
```kotlin
124                     else -> '?'
125                 }
126                 
127                 val code = String.format("%c%02X%02X", dtcType, dtcByte1 and 0x3F, dtcByte2)
!!!                            ^ error
128                 
129                 // Parse status
130                 val status = DTCStatus(

```

### style, MagicNumber (63)

Report magic numbers. Magic number is a numeric literal that is not defined as a constant and hence it's unclear what the purpose of this number is. It's better to declare such numbers as constants and give them a proper name. By default, -1, 0, 1, and 2 are not considered to be magic numbers.

[Documentation](https://detekt.dev/docs/rules/style#magicnumber)

* /home/yungblud/Desktop/SpaceTec/protocol/uds/src/main/java/com/spacetec/protocol/uds/UDSProtocol.kt:421:66
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
418         for (i in seed.indices) {
419             // More complex algorithm than simple XOR
420             val seedByte = seed[i].toInt() and 0xFF
421             val calculated = ((seedByte * 0x10) + level + i) xor 0x5A
!!!                                                                  ^ error
422             key[i] = (calculated and 0xFF).toByte()
423         }
424         return key

```

* /home/yungblud/Desktop/SpaceTec/protocol/uds/src/main/java/com/spacetec/protocol/uds/services/DiagnosticSessionControlService.kt:23:94
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
20             val sessionType = SessionType.fromId(request)
21             if (sessionType == null) {
22                 emit(UDSResult.Error(
23                     ProtocolError.InvalidRequest("Invalid session type: 0x${request.toString(16)}")
!!                                                                                              ^ error
24                 ))
25                 return@flow
26             }

```

* /home/yungblud/Desktop/SpaceTec/protocol/uds/src/main/java/com/spacetec/protocol/uds/services/DiagnosticSessionControlService.kt:79:48
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
76             if (response.isSuccess) {
77                 val responseData = response.getOrNull()
78                 if (responseData != null && responseData.size >= 2 && 
79                     responseData[0].toInt() == 0x50) { // Positive response SID
!!                                                ^ error
80                     Result.success(Unit)
81                 } else {
82                     Result.failure(Exception("Invalid response format"))

```

* /home/yungblud/Desktop/SpaceTec/protocol/uds/src/main/java/com/spacetec/protocol/uds/services/DiagnosticSessionControlService.kt:106:55
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
103     }
104     
105     override fun parseResponse(rawData: ByteArray): SessionType? {
106         if (rawData.size < 2 || rawData[0].toInt() != 0x50) { // Positive response SID
!!!                                                       ^ error
107             return null
108         }
109         

```

* /home/yungblud/Desktop/SpaceTec/protocol/uds/src/main/java/com/spacetec/protocol/uds/services/DiagnosticSessionControlService.kt:110:52
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
107             return null
108         }
109         
110         val sessionTypeId = rawData[1].toInt() and 0xFF
!!!                                                    ^ error
111         return SessionType.fromId(sessionTypeId)
112     }
113     

```

* /home/yungblud/Desktop/SpaceTec/protocol/uds/src/main/java/com/spacetec/protocol/uds/services/ECUResetService.kt:22:94
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
19             val resetType = request
20             if (!isValidResetType(resetType)) {
21                 emit(UDSResult.Error(
22                     ProtocolError.InvalidRequest("Invalid reset type: 0x${resetType.toString(16)}")
!!                                                                                              ^ error
23                 ))
24                 return@flow
25             }

```

* /home/yungblud/Desktop/SpaceTec/protocol/uds/src/main/java/com/spacetec/protocol/uds/services/ECUResetService.kt:45:35
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
42      * Validates the reset type according to ISO 14229
43      */
44     private fun isValidResetType(resetType: Int): Boolean {
45         return resetType in 0x01..0xFF // Valid range according to ISO 14229
!!                                   ^ error
46     }
47     
48     /**

```

* /home/yungblud/Desktop/SpaceTec/protocol/uds/src/main/java/com/spacetec/protocol/uds/services/ECUResetService.kt:60:48
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
57             if (response.isSuccess) {
58                 val responseData = response.getOrNull()
59                 if (responseData != null && responseData.size >= 2 && 
60                     responseData[0].toInt() == 0x51) { // Positive response SID
!!                                                ^ error
61                     Result.success(responseData[1].toInt() and 0xFF)
62                 } else {
63                     Result.failure(Exception("Invalid response format"))

```

* /home/yungblud/Desktop/SpaceTec/protocol/uds/src/main/java/com/spacetec/protocol/uds/services/ECUResetService.kt:61:64
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
58                 val responseData = response.getOrNull()
59                 if (responseData != null && responseData.size >= 2 && 
60                     responseData[0].toInt() == 0x51) { // Positive response SID
61                     Result.success(responseData[1].toInt() and 0xFF)
!!                                                                ^ error
62                 } else {
63                     Result.failure(Exception("Invalid response format"))
64                 }

```

* /home/yungblud/Desktop/SpaceTec/protocol/uds/src/main/java/com/spacetec/protocol/uds/services/ECUResetService.kt:87:55
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
84     }
85     
86     override fun parseResponse(rawData: ByteArray): Int? {
87         if (rawData.size < 2 || rawData[0].toInt() != 0x51) { // Positive response SID
!!                                                       ^ error
88             return null
89         }
90         

```

* /home/yungblud/Desktop/SpaceTec/protocol/uds/src/main/java/com/spacetec/protocol/uds/services/ECUResetService.kt:91:39
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
88             return null
89         }
90         
91         return rawData[1].toInt() and 0xFF
!!                                       ^ error
92     }
93     
94     companion object {

```

* /home/yungblud/Desktop/SpaceTec/protocol/uds/src/main/java/com/spacetec/protocol/uds/services/ReadDTCInformationService.kt:21:106
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
18         try {
19             if (!isValidSubFunction(request.subFunction)) {
20                 emit(UDSResult.Error(
21                     ProtocolError.InvalidRequest("Invalid sub-function: 0x${request.subFunction.toString(16)}")
!!                                                                                                          ^ error
22                 ))
23                 return@flow
24             }

```

* /home/yungblud/Desktop/SpaceTec/protocol/uds/src/main/java/com/spacetec/protocol/uds/services/ReadDTCInformationService.kt:44:37
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
41      * Validates the sub-function according to ISO 14229
42      */
43     private fun isValidSubFunction(subFunction: Int): Boolean {
44         return subFunction in 0x01..0xFF // Valid range according to ISO 14229
!!                                     ^ error
45     }
46     
47     /**

```

* /home/yungblud/Desktop/SpaceTec/protocol/uds/src/main/java/com/spacetec/protocol/uds/services/ReadDTCInformationService.kt:59:48
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
56             if (response.isSuccess) {
57                 val responseData = response.getOrNull()
58                 if (responseData != null && responseData.size >= 1 && 
59                     responseData[0].toInt() == 0x59) { // Positive response SID for ReadDTC
!!                                                ^ error
60                     val dtcs = parseDTCResponse(responseData, request)
61                     Result.success(dtcs)
62                 } else {

```

* /home/yungblud/Desktop/SpaceTec/protocol/uds/src/main/java/com/spacetec/protocol/uds/services/ReadDTCInformationService.kt:83:19
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
80         
81         // Add additional parameters based on sub-function
82         when (request.subFunction) {
83             0x02, 0x06, 0x0A, 0x0E, 0x10, 0x14, 0x18 -> {
!!                   ^ error
84                 // These sub-functions require DTC status mask
85                 data.add(request.dtcStatusMask.toByte())
86             }

```

* /home/yungblud/Desktop/SpaceTec/protocol/uds/src/main/java/com/spacetec/protocol/uds/services/ReadDTCInformationService.kt:83:25
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
80         
81         // Add additional parameters based on sub-function
82         when (request.subFunction) {
83             0x02, 0x06, 0x0A, 0x0E, 0x10, 0x14, 0x18 -> {
!!                         ^ error
84                 // These sub-functions require DTC status mask
85                 data.add(request.dtcStatusMask.toByte())
86             }

```

* /home/yungblud/Desktop/SpaceTec/protocol/uds/src/main/java/com/spacetec/protocol/uds/services/ReadDTCInformationService.kt:83:31
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
80         
81         // Add additional parameters based on sub-function
82         when (request.subFunction) {
83             0x02, 0x06, 0x0A, 0x0E, 0x10, 0x14, 0x18 -> {
!!                               ^ error
84                 // These sub-functions require DTC status mask
85                 data.add(request.dtcStatusMask.toByte())
86             }

```

* /home/yungblud/Desktop/SpaceTec/protocol/uds/src/main/java/com/spacetec/protocol/uds/services/ReadDTCInformationService.kt:83:37
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
80         
81         // Add additional parameters based on sub-function
82         when (request.subFunction) {
83             0x02, 0x06, 0x0A, 0x0E, 0x10, 0x14, 0x18 -> {
!!                                     ^ error
84                 // These sub-functions require DTC status mask
85                 data.add(request.dtcStatusMask.toByte())
86             }

```

* /home/yungblud/Desktop/SpaceTec/protocol/uds/src/main/java/com/spacetec/protocol/uds/services/ReadDTCInformationService.kt:83:43
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
80         
81         // Add additional parameters based on sub-function
82         when (request.subFunction) {
83             0x02, 0x06, 0x0A, 0x0E, 0x10, 0x14, 0x18 -> {
!!                                           ^ error
84                 // These sub-functions require DTC status mask
85                 data.add(request.dtcStatusMask.toByte())
86             }

```

* /home/yungblud/Desktop/SpaceTec/protocol/uds/src/main/java/com/spacetec/protocol/uds/services/ReadDTCInformationService.kt:83:49
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
80         
81         // Add additional parameters based on sub-function
82         when (request.subFunction) {
83             0x02, 0x06, 0x0A, 0x0E, 0x10, 0x14, 0x18 -> {
!!                                                 ^ error
84                 // These sub-functions require DTC status mask
85                 data.add(request.dtcStatusMask.toByte())
86             }

```

* /home/yungblud/Desktop/SpaceTec/protocol/uds/src/main/java/com/spacetec/protocol/uds/services/ReadDTCInformationService.kt:87:13
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
84                 // These sub-functions require DTC status mask
85                 data.add(request.dtcStatusMask.toByte())
86             }
87             0x03, 0x04, 0x0B, 0x0F, 0x15, 0x19 -> {
!!             ^ error
88                 // These sub-functions require DTC mask record
89                 data.addAll(request.dtcMaskRecord.map { it.toByte() })
90             }

```

* /home/yungblud/Desktop/SpaceTec/protocol/uds/src/main/java/com/spacetec/protocol/uds/services/ReadDTCInformationService.kt:87:19
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
84                 // These sub-functions require DTC status mask
85                 data.add(request.dtcStatusMask.toByte())
86             }
87             0x03, 0x04, 0x0B, 0x0F, 0x15, 0x19 -> {
!!                   ^ error
88                 // These sub-functions require DTC mask record
89                 data.addAll(request.dtcMaskRecord.map { it.toByte() })
90             }

```

* /home/yungblud/Desktop/SpaceTec/protocol/uds/src/main/java/com/spacetec/protocol/uds/services/ReadDTCInformationService.kt:87:25
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
84                 // These sub-functions require DTC status mask
85                 data.add(request.dtcStatusMask.toByte())
86             }
87             0x03, 0x04, 0x0B, 0x0F, 0x15, 0x19 -> {
!!                         ^ error
88                 // These sub-functions require DTC mask record
89                 data.addAll(request.dtcMaskRecord.map { it.toByte() })
90             }

```

* /home/yungblud/Desktop/SpaceTec/protocol/uds/src/main/java/com/spacetec/protocol/uds/services/ReadDTCInformationService.kt:87:37
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
84                 // These sub-functions require DTC status mask
85                 data.add(request.dtcStatusMask.toByte())
86             }
87             0x03, 0x04, 0x0B, 0x0F, 0x15, 0x19 -> {
!!                                     ^ error
88                 // These sub-functions require DTC mask record
89                 data.addAll(request.dtcMaskRecord.map { it.toByte() })
90             }

```

* /home/yungblud/Desktop/SpaceTec/protocol/uds/src/main/java/com/spacetec/protocol/uds/services/ReadDTCInformationService.kt:87:43
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
84                 // These sub-functions require DTC status mask
85                 data.add(request.dtcStatusMask.toByte())
86             }
87             0x03, 0x04, 0x0B, 0x0F, 0x15, 0x19 -> {
!!                                           ^ error
88                 // These sub-functions require DTC mask record
89                 data.addAll(request.dtcMaskRecord.map { it.toByte() })
90             }

```

* /home/yungblud/Desktop/SpaceTec/protocol/uds/src/main/java/com/spacetec/protocol/uds/services/ReadDTCInformationService.kt:91:13
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
88                 // These sub-functions require DTC mask record
89                 data.addAll(request.dtcMaskRecord.map { it.toByte() })
90             }
91             0x07, 0x08, 0x0C, 0x11, 0x16, 0x1A -> {
!!             ^ error
92                 // These sub-functions require snapshot record number
93                 data.add(request.snapshotRecordNumber.toByte())
94             }

```

* /home/yungblud/Desktop/SpaceTec/protocol/uds/src/main/java/com/spacetec/protocol/uds/services/ReadDTCInformationService.kt:91:19
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
88                 // These sub-functions require DTC mask record
89                 data.addAll(request.dtcMaskRecord.map { it.toByte() })
90             }
91             0x07, 0x08, 0x0C, 0x11, 0x16, 0x1A -> {
!!                   ^ error
92                 // These sub-functions require snapshot record number
93                 data.add(request.snapshotRecordNumber.toByte())
94             }

```

* /home/yungblud/Desktop/SpaceTec/protocol/uds/src/main/java/com/spacetec/protocol/uds/services/ReadDTCInformationService.kt:91:25
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
88                 // These sub-functions require DTC mask record
89                 data.addAll(request.dtcMaskRecord.map { it.toByte() })
90             }
91             0x07, 0x08, 0x0C, 0x11, 0x16, 0x1A -> {
!!                         ^ error
92                 // These sub-functions require snapshot record number
93                 data.add(request.snapshotRecordNumber.toByte())
94             }

```

* /home/yungblud/Desktop/SpaceTec/protocol/uds/src/main/java/com/spacetec/protocol/uds/services/ReadDTCInformationService.kt:91:31
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
88                 // These sub-functions require DTC mask record
89                 data.addAll(request.dtcMaskRecord.map { it.toByte() })
90             }
91             0x07, 0x08, 0x0C, 0x11, 0x16, 0x1A -> {
!!                               ^ error
92                 // These sub-functions require snapshot record number
93                 data.add(request.snapshotRecordNumber.toByte())
94             }

```

* /home/yungblud/Desktop/SpaceTec/protocol/uds/src/main/java/com/spacetec/protocol/uds/services/ReadDTCInformationService.kt:91:37
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
88                 // These sub-functions require DTC mask record
89                 data.addAll(request.dtcMaskRecord.map { it.toByte() })
90             }
91             0x07, 0x08, 0x0C, 0x11, 0x16, 0x1A -> {
!!                                     ^ error
92                 // These sub-functions require snapshot record number
93                 data.add(request.snapshotRecordNumber.toByte())
94             }

```

* /home/yungblud/Desktop/SpaceTec/protocol/uds/src/main/java/com/spacetec/protocol/uds/services/ReadDTCInformationService.kt:91:43
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
88                 // These sub-functions require DTC mask record
89                 data.addAll(request.dtcMaskRecord.map { it.toByte() })
90             }
91             0x07, 0x08, 0x0C, 0x11, 0x16, 0x1A -> {
!!                                           ^ error
92                 // These sub-functions require snapshot record number
93                 data.add(request.snapshotRecordNumber.toByte())
94             }

```

* /home/yungblud/Desktop/SpaceTec/protocol/uds/src/main/java/com/spacetec/protocol/uds/services/ReadDTCInformationService.kt:95:13
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
92                 // These sub-functions require snapshot record number
93                 data.add(request.snapshotRecordNumber.toByte())
94             }
95             0x09, 0x12, 0x17, 0x1B -> {
!!             ^ error
96                 // These sub-functions require extended data record number
97                 data.add(request.extendedDataRecordNumber.toByte())
98             }

```

* /home/yungblud/Desktop/SpaceTec/protocol/uds/src/main/java/com/spacetec/protocol/uds/services/ReadDTCInformationService.kt:95:19
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
92                 // These sub-functions require snapshot record number
93                 data.add(request.snapshotRecordNumber.toByte())
94             }
95             0x09, 0x12, 0x17, 0x1B -> {
!!                   ^ error
96                 // These sub-functions require extended data record number
97                 data.add(request.extendedDataRecordNumber.toByte())
98             }

```

* /home/yungblud/Desktop/SpaceTec/protocol/uds/src/main/java/com/spacetec/protocol/uds/services/ReadDTCInformationService.kt:95:25
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
92                 // These sub-functions require snapshot record number
93                 data.add(request.snapshotRecordNumber.toByte())
94             }
95             0x09, 0x12, 0x17, 0x1B -> {
!!                         ^ error
96                 // These sub-functions require extended data record number
97                 data.add(request.extendedDataRecordNumber.toByte())
98             }

```

* /home/yungblud/Desktop/SpaceTec/protocol/uds/src/main/java/com/spacetec/protocol/uds/services/ReadDTCInformationService.kt:95:31
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
92                 // These sub-functions require snapshot record number
93                 data.add(request.snapshotRecordNumber.toByte())
94             }
95             0x09, 0x12, 0x17, 0x1B -> {
!!                               ^ error
96                 // These sub-functions require extended data record number
97                 data.add(request.extendedDataRecordNumber.toByte())
98             }

```

* /home/yungblud/Desktop/SpaceTec/protocol/uds/src/main/java/com/spacetec/protocol/uds/services/ReadDTCInformationService.kt:113:23
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
110         // Skip SID (0x59) and sub-function
111         var idx = 2
112         while (idx < responseData.size) {
113             if (idx + 3 < responseData.size) { // Need at least 4 bytes for a DTC entry
!!!                       ^ error
114                 val dtcByte1 = responseData[idx].toInt() and 0xFF
115                 val dtcByte2 = responseData[idx + 1].toInt() and 0xFF
116                 val dtcByte3 = responseData[idx + 2].toInt() and 0xFF

```

* /home/yungblud/Desktop/SpaceTec/protocol/uds/src/main/java/com/spacetec/protocol/uds/services/ReadDTCInformationService.kt:114:62
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
111         var idx = 2
112         while (idx < responseData.size) {
113             if (idx + 3 < responseData.size) { // Need at least 4 bytes for a DTC entry
114                 val dtcByte1 = responseData[idx].toInt() and 0xFF
!!!                                                              ^ error
115                 val dtcByte2 = responseData[idx + 1].toInt() and 0xFF
116                 val dtcByte3 = responseData[idx + 2].toInt() and 0xFF
117                 

```

* /home/yungblud/Desktop/SpaceTec/protocol/uds/src/main/java/com/spacetec/protocol/uds/services/ReadDTCInformationService.kt:115:66
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
112         while (idx < responseData.size) {
113             if (idx + 3 < responseData.size) { // Need at least 4 bytes for a DTC entry
114                 val dtcByte1 = responseData[idx].toInt() and 0xFF
115                 val dtcByte2 = responseData[idx + 1].toInt() and 0xFF
!!!                                                                  ^ error
116                 val dtcByte3 = responseData[idx + 2].toInt() and 0xFF
117                 
118                 // Parse DTC according to ISO 15031-6

```

* /home/yungblud/Desktop/SpaceTec/protocol/uds/src/main/java/com/spacetec/protocol/uds/services/ReadDTCInformationService.kt:116:66
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
113             if (idx + 3 < responseData.size) { // Need at least 4 bytes for a DTC entry
114                 val dtcByte1 = responseData[idx].toInt() and 0xFF
115                 val dtcByte2 = responseData[idx + 1].toInt() and 0xFF
116                 val dtcByte3 = responseData[idx + 2].toInt() and 0xFF
!!!                                                                  ^ error
117                 
118                 // Parse DTC according to ISO 15031-6
119                 val dtcType = when ((dtcByte1 and 0xC0) shr 6) {

```

* /home/yungblud/Desktop/SpaceTec/protocol/uds/src/main/java/com/spacetec/protocol/uds/services/ReadDTCInformationService.kt:119:51
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
116                 val dtcByte3 = responseData[idx + 2].toInt() and 0xFF
117                 
118                 // Parse DTC according to ISO 15031-6
119                 val dtcType = when ((dtcByte1 and 0xC0) shr 6) {
!!!                                                   ^ error
120                     0 -> 'P' // Powertrain
121                     1 -> 'C' // Chassis
122                     2 -> 'B' // Body

```

* /home/yungblud/Desktop/SpaceTec/protocol/uds/src/main/java/com/spacetec/protocol/uds/services/ReadDTCInformationService.kt:119:61
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
116                 val dtcByte3 = responseData[idx + 2].toInt() and 0xFF
117                 
118                 // Parse DTC according to ISO 15031-6
119                 val dtcType = when ((dtcByte1 and 0xC0) shr 6) {
!!!                                                             ^ error
120                     0 -> 'P' // Powertrain
121                     1 -> 'C' // Chassis
122                     2 -> 'B' // Body

```

* /home/yungblud/Desktop/SpaceTec/protocol/uds/src/main/java/com/spacetec/protocol/uds/services/ReadDTCInformationService.kt:123:21
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
120                     0 -> 'P' // Powertrain
121                     1 -> 'C' // Chassis
122                     2 -> 'B' // Body
123                     3 -> 'U' // Network
!!!                     ^ error
124                     else -> '?'
125                 }
126                 

```

* /home/yungblud/Desktop/SpaceTec/protocol/uds/src/main/java/com/spacetec/protocol/uds/services/ReadDTCInformationService.kt:127:78
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
124                     else -> '?'
125                 }
126                 
127                 val code = String.format("%c%02X%02X", dtcType, dtcByte1 and 0x3F, dtcByte2)
!!!                                                                              ^ error
128                 
129                 // Parse status
130                 val status = DTCStatus(

```

* /home/yungblud/Desktop/SpaceTec/protocol/uds/src/main/java/com/spacetec/protocol/uds/services/ReadDTCInformationService.kt:142:24
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
139                 )
140                 
141                 dtcs.add(DTC(code, "", status))
142                 idx += 4 // Move to next DTC entry
!!!                        ^ error
143             } else {
144                 break
145             }

```

* /home/yungblud/Desktop/SpaceTec/protocol/uds/src/main/java/com/spacetec/protocol/uds/services/ReadDTCInformationService.kt:165:55
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
162     }
163     
164     override fun parseResponse(rawData: ByteArray): List<DTC>? {
165         if (rawData.size < 2 || rawData[0].toInt() != 0x59) { // Positive response SID
!!!                                                       ^ error
166             return null
167         }
168         

```

* /home/yungblud/Desktop/SpaceTec/protocol/uds/src/main/java/com/spacetec/protocol/uds/services/ReadDataByIdentifierService.kt:21:97
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
18         try {
19             if (!isValidDataIdentifier(request)) {
20                 emit(UDSResult.Error(
21                     ProtocolError.InvalidRequest("Invalid data identifier: 0x${request.toString(16)}")
!!                                                                                                 ^ error
22                 ))
23                 return@flow
24             }

```

* /home/yungblud/Desktop/SpaceTec/protocol/uds/src/main/java/com/spacetec/protocol/uds/services/ReadDataByIdentifierService.kt:46:31
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
43     private fun isValidDataIdentifier(did: Int): Boolean {
44         // Standard DIDs range from 0x0000 to 0xFFFF
45         // Exclude reserved ranges as per ISO 14229
46         return did in 0x0000..0xFFFF
!!                               ^ error
47     }
48     
49     /**

```

* /home/yungblud/Desktop/SpaceTec/protocol/uds/src/main/java/com/spacetec/protocol/uds/services/ReadDataByIdentifierService.kt:57:26
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
54             // Format request: SID(0x22) + DID (2 bytes, MSB first)
55             val requestData = byteArrayOf(
56                 0x22.toByte(),
57                 (did shr 8).toByte(),  // MSB
!!                          ^ error
58                 did.toByte()           // LSB
59             )
60             

```

* /home/yungblud/Desktop/SpaceTec/protocol/uds/src/main/java/com/spacetec/protocol/uds/services/ReadDataByIdentifierService.kt:65:66
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
62             
63             if (response.isSuccess) {
64                 val responseData = response.getOrNull()
65                 if (responseData != null && responseData.size >= 3 && 
!!                                                                  ^ error
66                     responseData[0].toInt() == 0x62) { // Positive response SID for ReadDataByIdentifier
67                     // Extract the actual data (skip SID and DID)
68                     val actualData = responseData.sliceArray(3 until responseData.size)

```

* /home/yungblud/Desktop/SpaceTec/protocol/uds/src/main/java/com/spacetec/protocol/uds/services/ReadDataByIdentifierService.kt:66:48
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
63             if (response.isSuccess) {
64                 val responseData = response.getOrNull()
65                 if (responseData != null && responseData.size >= 3 && 
66                     responseData[0].toInt() == 0x62) { // Positive response SID for ReadDataByIdentifier
!!                                                ^ error
67                     // Extract the actual data (skip SID and DID)
68                     val actualData = responseData.sliceArray(3 until responseData.size)
69                     Result.success(actualData)

```

* /home/yungblud/Desktop/SpaceTec/protocol/uds/src/main/java/com/spacetec/protocol/uds/services/ReadDataByIdentifierService.kt:68:62
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
65                 if (responseData != null && responseData.size >= 3 && 
66                     responseData[0].toInt() == 0x62) { // Positive response SID for ReadDataByIdentifier
67                     // Extract the actual data (skip SID and DID)
68                     val actualData = responseData.sliceArray(3 until responseData.size)
!!                                                              ^ error
69                     Result.success(actualData)
70                 } else {
71                     Result.failure(Exception("Invalid response format"))

```

* /home/yungblud/Desktop/SpaceTec/protocol/uds/src/main/java/com/spacetec/protocol/uds/services/ReadDataByIdentifierService.kt:88:86
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
85         // This would interface with the actual UDS protocol implementation
86         // For now, return a simulated response
87         // Simulate response: 0x62 (positive response SID) + DID + actual data
88         val simulatedData = byteArrayOf(0x62.toByte(), data[1], data[2], 0x01, 0x02, 0x03, 0x04)
!!                                                                                      ^ error
89         return Result.success(simulatedData)
90     }
91     

```

* /home/yungblud/Desktop/SpaceTec/protocol/uds/src/main/java/com/spacetec/protocol/uds/services/ReadDataByIdentifierService.kt:88:92
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
85         // This would interface with the actual UDS protocol implementation
86         // For now, return a simulated response
87         // Simulate response: 0x62 (positive response SID) + DID + actual data
88         val simulatedData = byteArrayOf(0x62.toByte(), data[1], data[2], 0x01, 0x02, 0x03, 0x04)
!!                                                                                            ^ error
89         return Result.success(simulatedData)
90     }
91     

```

* /home/yungblud/Desktop/SpaceTec/protocol/uds/src/main/java/com/spacetec/protocol/uds/services/ReadDataByIdentifierService.kt:95:26
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
92     override fun formatRequest(request: Int): ByteArray {
93         return byteArrayOf(
94             0x22.toByte(),
95             (request shr 8).toByte(),  // MSB
!!                          ^ error
96             request.toByte()           // LSB
97         )
98     }

```

* /home/yungblud/Desktop/SpaceTec/protocol/uds/src/main/java/com/spacetec/protocol/uds/services/ReadDataByIdentifierService.kt:101:28
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
98      }
99      
100     override fun parseResponse(rawData: ByteArray): ByteArray? {
101         if (rawData.size < 3 || rawData[0].toInt() != 0x62) { // Positive response SID
!!!                            ^ error
102             return null
103         }
104         

```

* /home/yungblud/Desktop/SpaceTec/protocol/uds/src/main/java/com/spacetec/protocol/uds/services/ReadDataByIdentifierService.kt:101:55
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
98      }
99      
100     override fun parseResponse(rawData: ByteArray): ByteArray? {
101         if (rawData.size < 3 || rawData[0].toInt() != 0x62) { // Positive response SID
!!!                                                       ^ error
102             return null
103         }
104         

```

* /home/yungblud/Desktop/SpaceTec/protocol/uds/src/main/java/com/spacetec/protocol/uds/services/ReadDataByIdentifierService.kt:106:35
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
103         }
104         
105         // Extract the actual data (skip SID and DID)
106         return rawData.sliceArray(3 until rawData.size)
!!!                                   ^ error
107     }
108     
109     companion object {

```

* /home/yungblud/Desktop/SpaceTec/protocol/uds/src/main/java/com/spacetec/protocol/uds/services/SecurityAccessService.kt:23:121
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
20         try {
21             if (!isValidSecurityAccessType(request.securityAccessType)) {
22                 emit(UDSResult.Error(
23                     ProtocolError.InvalidRequest("Invalid security access type: 0x${request.securityAccessType.toString(16)}")
!!                                                                                                                         ^ error
24                 ))
25                 return@flow
26             }

```

* /home/yungblud/Desktop/SpaceTec/protocol/uds/src/main/java/com/spacetec/protocol/uds/services/SecurityAccessService.kt:51:65
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
48         val isEven = securityAccessType and 0x01 == 0
49         
50         // Security access type 0x00 and 0xFF are reserved
51         if (securityAccessType == 0x00 || securityAccessType == 0xFF) {
!!                                                                 ^ error
52             return false
53         }
54         

```

* /home/yungblud/Desktop/SpaceTec/protocol/uds/src/main/java/com/spacetec/protocol/uds/services/SecurityAccessService.kt:56:50
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
53         }
54         
55         // For odd values (request seed), the range is 0x01-0x7F
56         if (isOdd && securityAccessType in 0x01..0x7F) {
!!                                                  ^ error
57             return true
58         }
59         

```

* /home/yungblud/Desktop/SpaceTec/protocol/uds/src/main/java/com/spacetec/protocol/uds/services/SecurityAccessService.kt:61:51
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
58         }
59         
60         // For even values (send key), the range is 0x02-0xFE
61         if (isEven && securityAccessType in 0x02..0xFE) {
!!                                                   ^ error
62             return true
63         }
64         

```

* /home/yungblud/Desktop/SpaceTec/protocol/uds/src/main/java/com/spacetec/protocol/uds/services/SecurityAccessService.kt:79:48
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
76             if (response.isSuccess) {
77                 val responseData = response.getOrNull()
78                 if (responseData != null && responseData.size >= 2 && 
79                     responseData[0].toInt() == 0x67) { // Positive response SID for SecurityAccess
!!                                                ^ error
80                     Result.success(responseData)
81                 } else {
82                     Result.failure(Exception("Invalid response format"))

```

* /home/yungblud/Desktop/SpaceTec/protocol/uds/src/main/java/com/spacetec/protocol/uds/services/SecurityAccessService.kt:150:55
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
147     }
148     
149     override fun parseResponse(rawData: ByteArray): ByteArray? {
150         if (rawData.size < 2 || rawData[0].toInt() != 0x67) { // Positive response SID
!!!                                                       ^ error
151             return null
152         }
153         

```

### style, MaxLineLength (4)

Line detected, which is longer than the defined maximum line length in the code style.

[Documentation](https://detekt.dev/docs/rules/style#maxlinelength)

* /home/yungblud/Desktop/SpaceTec/protocol/uds/src/main/java/com/spacetec/protocol/uds/services/ReadDTCInformationService.kt:32:1
```
Line detected, which is longer than the defined maximum line length in the code style.
```
```kotlin
29                 emit(UDSResult.Success(dtcs))
30             } else {
31                 emit(UDSResult.Error(
32                     ProtocolError.CommunicationError("Read DTC information failed: ${result.exceptionOrNull()?.message}")
!! ^ error
33                 ))
34             }
35         } catch (e: Exception) {

```

* /home/yungblud/Desktop/SpaceTec/protocol/uds/src/main/java/com/spacetec/protocol/uds/services/ReadDataByIdentifierService.kt:32:1
```
Line detected, which is longer than the defined maximum line length in the code style.
```
```kotlin
29                 emit(UDSResult.Success(data))
30             } else {
31                 emit(UDSResult.Error(
32                     ProtocolError.CommunicationError("Read data by identifier failed: ${result.exceptionOrNull()?.message}")
!! ^ error
33                 ))
34             }
35         } catch (e: Exception) {

```

* /home/yungblud/Desktop/SpaceTec/protocol/uds/src/main/java/com/spacetec/protocol/uds/services/SecurityAccessService.kt:23:1
```
Line detected, which is longer than the defined maximum line length in the code style.
```
```kotlin
20         try {
21             if (!isValidSecurityAccessType(request.securityAccessType)) {
22                 emit(UDSResult.Error(
23                     ProtocolError.InvalidRequest("Invalid security access type: 0x${request.securityAccessType.toString(16)}")
!! ^ error
24                 ))
25                 return@flow
26             }

```

* /home/yungblud/Desktop/SpaceTec/protocol/uds/src/main/java/com/spacetec/protocol/uds/services/SecurityAccessService.kt:116:1
```
Line detected, which is longer than the defined maximum line length in the code style.
```
```kotlin
113         // For now, return a simulated response
114         return if (data[1].toInt() and 0x01 == 1) {
115             // Requesting seed (odd number) - return a simulated seed
116             Result.success(byteArrayOf(0x67.toByte(), data[1], 0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte()))
!!! ^ error
117         } else {
118             // Sending key (even number) - return success response
119             Result.success(byteArrayOf(0x67.toByte(), data[1]))

```

### style, NewLineAtEndOfFile (5)

Checks whether files end with a line separator.

[Documentation](https://detekt.dev/docs/rules/style#newlineatendoffile)

* /home/yungblud/Desktop/SpaceTec/protocol/uds/src/main/java/com/spacetec/protocol/uds/services/DiagnosticSessionControlService.kt:118:2
```
The file /home/yungblud/Desktop/SpaceTec/protocol/uds/src/main/java/com/spacetec/protocol/uds/services/DiagnosticSessionControlService.kt is not ending with a new line.
```
```kotlin
115         val isValid: Boolean,
116         val message: String
117     )
118 }
!!!  ^ error

```

* /home/yungblud/Desktop/SpaceTec/protocol/uds/src/main/java/com/spacetec/protocol/uds/services/ECUResetService.kt:102:2
```
The file /home/yungblud/Desktop/SpaceTec/protocol/uds/src/main/java/com/spacetec/protocol/uds/services/ECUResetService.kt is not ending with a new line.
```
```kotlin
99          const val ENABLE_RAPID_POWER_SHUTDOWN = 0x04
100         const val DISABLE_RAPID_POWER_SHUTDOWN = 0x05
101     }
102 }
!!!  ^ error

```

* /home/yungblud/Desktop/SpaceTec/protocol/uds/src/main/java/com/spacetec/protocol/uds/services/ReadDTCInformationService.kt:229:2
```
The file /home/yungblud/Desktop/SpaceTec/protocol/uds/src/main/java/com/spacetec/protocol/uds/services/ReadDTCInformationService.kt is not ending with a new line.
```
```kotlin
226         const val REPORT_PREVIOUSLY_DETECTED_DTC = 0x1B
227         const val REPORT_NUM_PREVIOUSLY_DETECTED_DTC = 0x1C
228     }
229 }
!!!  ^ error

```

* /home/yungblud/Desktop/SpaceTec/protocol/uds/src/main/java/com/spacetec/protocol/uds/services/ReadDataByIdentifierService.kt:132:2
```
The file /home/yungblud/Desktop/SpaceTec/protocol/uds/src/main/java/com/spacetec/protocol/uds/services/ReadDataByIdentifierService.kt is not ending with a new line.
```
```kotlin
129         const val OBD_SPN_LIST_TEMP = 0xF1A7
130         const val OBD_FMI_LIST_TEMP = 0xF1A8
131     }
132 }
!!!  ^ error

```

* /home/yungblud/Desktop/SpaceTec/protocol/uds/src/main/java/com/spacetec/protocol/uds/services/SecurityAccessService.kt:184:2
```
The file /home/yungblud/Desktop/SpaceTec/protocol/uds/src/main/java/com/spacetec/protocol/uds/services/SecurityAccessService.kt is not ending with a new line.
```
```kotlin
181         const val SEND_KEY_LEVEL_8 = 0x10
182         // Additional levels continue up to 0x7F (request seed) and 0xFE (send key)
183     }
184 }
!!!  ^ error

```

### style, ReturnCount (1)

Restrict the number of return statements in methods.

[Documentation](https://detekt.dev/docs/rules/style#returncount)

* /home/yungblud/Desktop/SpaceTec/protocol/uds/src/main/java/com/spacetec/protocol/uds/services/SecurityAccessService.kt:45:17
```
Function isValidSecurityAccessType has 4 return statements which exceeds the limit of 2.
```
```kotlin
42     /**
43      * Validates the security access type according to ISO 14229
44      */
45     private fun isValidSecurityAccessType(securityAccessType: Int): Boolean {
!!                 ^ error
46         // Odd values are for requesting seed, even values are for sending key
47         val isOdd = securityAccessType and 0x01 == 1
48         val isEven = securityAccessType and 0x01 == 0

```

### style, UnusedParameter (1)

Function parameter is unused and should be removed.

[Documentation](https://detekt.dev/docs/rules/style#unusedparameter)

* /home/yungblud/Desktop/SpaceTec/protocol/uds/src/main/java/com/spacetec/protocol/uds/services/ReadDTCInformationService.kt:107:59
```
Function parameter `request` is unused.
```
```kotlin
104     /**
105      * Parses the DTC response based on the sub-function
106      */
107     private fun parseDTCResponse(responseData: ByteArray, request: ReadDTCRequest): List<DTC> {
!!!                                                           ^ error
108         val dtcs = mutableListOf<DTC>()
109         
110         // Skip SID (0x59) and sub-function

```

generated with [detekt version 1.23.4](https://detekt.dev/) on 2025-12-25 22:42:25 UTC

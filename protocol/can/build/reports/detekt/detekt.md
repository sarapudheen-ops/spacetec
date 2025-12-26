# detekt

## Metrics

* 69 number of properties

* 37 number of functions

* 9 number of classes

* 3 number of packages

* 8 number of kt files

## Complexity Report

* 703 lines of code (loc)

* 441 source lines of code (sloc)

* 337 logical lines of code (lloc)

* 183 comment lines of code (cloc)

* 80 cyclomatic complexity (mcc)

* 65 cognitive complexity

* 97 number of total code smells

* 41% comment source ratio

* 237 mcc per 1,000 lloc

* 287 code smells per 1,000 lloc

## Findings (97)

### exceptions, SwallowedException (3)

The caught exception is swallowed. The original exception could be lost.

[Documentation](https://detekt.dev/docs/rules/exceptions#swallowedexception)

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/CANFrame.kt:44:22
```
The caught exception is swallowed. The original exception could be lost.
```
```kotlin
41         fun create(canId: Int, data: ByteArray, isExtendedFrame: Boolean = true): CANFrame? {
42             return try {
43                 CANFrame(canId, data, isExtendedFrame)
44             } catch (e: IllegalArgumentException) {
!!                      ^ error
45                 null
46             }
47         }

```

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/isotp/ISOTPProtocol.kt:104:22
```
The caught exception is swallowed. The original exception could be lost.
```
```kotlin
101             try {
102                 val request = buildRequest(0x3E, byteArrayOf(0x00)) // Tester Present
103                 sendMessage(request, _config.responseTimeoutMs)
104             } catch (e: Exception) {
!!!                      ^ error
105                 // Keep-alive is best effort
106             }
107         }

```

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/isotp/ISOTPProtocol.kt:168:18
```
The caught exception is swallowed. The original exception could be lost.
```
```kotlin
165         try {
166             // Reset CAN adapter settings
167             sendRaw("ATZ\r".toByteArray(), 500)
168         } catch (e: Exception) {
!!!                  ^ error
169             // Ignore errors during shutdown
170         }
171         isInitialized.set(false)

```

### exceptions, TooGenericExceptionCaught (3)

The caught exception is too generic. Prefer catching specific exceptions to the case that is currently handled.

[Documentation](https://detekt.dev/docs/rules/exceptions#toogenericexceptioncaught)

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/isotp/ISOTPProtocol.kt:50:18
```
The caught exception is too generic. Prefer catching specific exceptions to the case that is currently handled.
```
```kotlin
47             
48             isInitialized.set(true)
49             completeInitialization()
50         } catch (e: Exception) {
!!                  ^ error
51             _state.value = ProtocolState.Error(
52                 com.spacetec.protocol.core.base.ProtocolError.CommunicationError("ISO-TP init failed: ${e.message}")
53             )

```

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/isotp/ISOTPProtocol.kt:104:22
```
The caught exception is too generic. Prefer catching specific exceptions to the case that is currently handled.
```
```kotlin
101             try {
102                 val request = buildRequest(0x3E, byteArrayOf(0x00)) // Tester Present
103                 sendMessage(request, _config.responseTimeoutMs)
104             } catch (e: Exception) {
!!!                      ^ error
105                 // Keep-alive is best effort
106             }
107         }

```

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/isotp/ISOTPProtocol.kt:168:18
```
The caught exception is too generic. Prefer catching specific exceptions to the case that is currently handled.
```
```kotlin
165         try {
166             // Reset CAN adapter settings
167             sendRaw("ATZ\r".toByteArray(), 500)
168         } catch (e: Exception) {
!!!                  ^ error
169             // Ignore errors during shutdown
170         }
171         isInitialized.set(false)

```

### exceptions, TooGenericExceptionThrown (1)

The thrown exception is too generic. Prefer throwing project specific exceptions to handle error cases.

[Documentation](https://detekt.dev/docs/rules/exceptions#toogenericexceptionthrown)

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/isotp/ISOTPFrameHandler.kt:48:13
```
Exception is a too generic Exception. Prefer throwing specific exceptions that indicate a specific error case.
```
```kotlin
45         flowControlReceived = true
46         
47         if (!flowControlReceived) {
48             throw Exception("Flow control frame not received")
!!             ^ error
49         }
50         
51         // Send consecutive frames according to flow control

```

### potential-bugs, ImplicitDefaultLocale (4)

Implicit default locale used for string processing. Consider using explicit locale.

[Documentation](https://detekt.dev/docs/rules/potential-bugs#implicitdefaultlocale)

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/CANFrame.kt:30:91
```
String.format("0x%02X", it) uses implicitly default locale for string formatting.
```
```kotlin
27     override fun getType(): FrameType = if (isExtendedFrame) FrameType.EXTENDED_CAN else FrameType.STANDARD_CAN
28     
29     override fun toString(): String {
30         return "CANFrame(canId=0x${canId.toString(16)}, data=[${data.joinToString(", ") { String.format("0x%02X", it) }}], extended=${isExtendedFrame})"
!!                                                                                           ^ error
31     }
32     
33     companion object {

```

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/CANMessage.kt:22:99
```
String.format("%02X", it) uses implicitly default locale for string formatting.
```
```kotlin
19     override fun toByteArray(): ByteArray = data
20     
21     override fun toString(): String {
22         return "CANMessage(serviceId=0x${serviceId.toString(16)}, data=${data.joinToString(" ") { String.format("%02X", it) }}, isNegativeResponse=$isNegativeResponse, nrc=${if (isNegativeResponse) String.format("0x%02X", negativeResponseCode) else "N/A"})"
!!                                                                                                   ^ error
23     }
24 }

```

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/CANMessage.kt:22:199
```
String.format("0x%02X", negativeResponseCode) uses implicitly default locale for string formatting.
```
```kotlin
19     override fun toByteArray(): ByteArray = data
20     
21     override fun toString(): String {
22         return "CANMessage(serviceId=0x${serviceId.toString(16)}, data=${data.joinToString(" ") { String.format("%02X", it) }}, isNegativeResponse=$isNegativeResponse, nrc=${if (isNegativeResponse) String.format("0x%02X", negativeResponseCode) else "N/A"})"
!!                                                                                                                                                                                                       ^ error
23     }
24 }

```

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/isotp/ISOTPProtocol.kt:124:69
```
String.format("0x%02X", nrc) uses implicitly default locale for string formatting.
```
```kotlin
121         nrc: Int,
122         request: DiagnosticMessage
123     ): DiagnosticMessage {
124         throw ProtocolException("Negative response in ISO-TP: NRC ${String.format("0x%02X", nrc)}")
!!!                                                                     ^ error
125     }
126     
127     override fun buildRequest(

```

### style, MagicNumber (71)

Report magic numbers. Magic number is a numeric literal that is not defined as a constant and hence it's unclear what the purpose of this number is. It's better to declare such numbers as constants and give them a proper name. By default, -1, 0, 1, and 2 are not considered to be magic numbers.

[Documentation](https://detekt.dev/docs/rules/style#magicnumber)

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/CANFilter.kt:34:40
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
31      */
32     companion object {
33         fun forExactId(canId: Int, isExtended: Boolean = true): CANFilter {
34             val mask = if (isExtended) 0x1FFFFFFF else 0x7FF // 29-bit or 11-bit mask
!!                                        ^ error
35             return CANFilter(canId, mask, isExtended)
36         }
37         

```

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/CANFilter.kt:34:56
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
31      */
32     companion object {
33         fun forExactId(canId: Int, isExtended: Boolean = true): CANFilter {
34             val mask = if (isExtended) 0x1FFFFFFF else 0x7FF // 29-bit or 11-bit mask
!!                                                        ^ error
35             return CANFilter(canId, mask, isExtended)
36         }
37         

```

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/CANFrame.kt:19:30
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
16 ) : Frame {
17     
18     init {
19         require(data.size <= 8) { "CAN frame data cannot exceed 8 bytes, got ${data.size} bytes" }
!!                              ^ error
20     }
21     
22     override val length: Int

```

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/CANFrame.kt:30:51
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
27     override fun getType(): FrameType = if (isExtendedFrame) FrameType.EXTENDED_CAN else FrameType.STANDARD_CAN
28     
29     override fun toString(): String {
30         return "CANFrame(canId=0x${canId.toString(16)}, data=[${data.joinToString(", ") { String.format("0x%02X", it) }}], extended=${isExtendedFrame})"
!!                                                   ^ error
31     }
32     
33     companion object {

```

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/CANMessage.kt:22:61
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
19     override fun toByteArray(): ByteArray = data
20     
21     override fun toString(): String {
22         return "CANMessage(serviceId=0x${serviceId.toString(16)}, data=${data.joinToString(" ") { String.format("%02X", it) }}, isNegativeResponse=$isNegativeResponse, nrc=${if (isNegativeResponse) String.format("0x%02X", negativeResponseCode) else "N/A"})"
!!                                                             ^ error
23     }
24 }

```

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/isotp/ISOTPFrameHandler.kt:39:15
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
36         val response = connection.sendCommand(bytesToHex(firstFrame))
37         
38         // Wait for flow control frame (should come quickly)
39         delay(50) // Wait a bit for flow control
!!               ^ error
40         
41         // In a real implementation, we'd parse the response to get flow control info
42         // For now, assume standard flow control

```

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/isotp/ISOTPFrameHandler.kt:55:23
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
52         for (i in 1 until frames.size) {
53             if (blockSize > 0 && i % blockSize == 0) {
54                 // Wait for next flow control if block size is limited
55                 delay(100)
!!                       ^ error
56             }
57             
58             val cfResponse = connection.sendCommand(bytesToHex(frames[i]))

```

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/isotp/ISOTPFrameHandler.kt:61:39
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
58             val cfResponse = connection.sendCommand(bytesToHex(frames[i]))
59             
60             if (separationTime > 0) {
61                 if (separationTime <= 0x7F) {
!!                                       ^ error
62                     // Time in milliseconds
63                     delay(separationTime.toLong())
64                 } else if (separationTime in 0xF1..0xF9) {

```

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/isotp/ISOTPFrameHandler.kt:64:46
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
61                 if (separationTime <= 0x7F) {
62                     // Time in milliseconds
63                     delay(separationTime.toLong())
64                 } else if (separationTime in 0xF1..0xF9) {
!!                                              ^ error
65                     // Time in 100-900 microseconds
66                     delay(1) // Minimum delay for this implementation
67                 }

```

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/isotp/ISOTPFrameHandler.kt:64:52
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
61                 if (separationTime <= 0x7F) {
62                     // Time in milliseconds
63                     delay(separationTime.toLong())
64                 } else if (separationTime in 0xF1..0xF9) {
!!                                                    ^ error
65                     // Time in 100-900 microseconds
66                     delay(1) // Minimum delay for this implementation
67                 }

```

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/isotp/ISOTPFrameHandler.kt:102:61
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
99          val cleanHex = hex.replace(" ", "").replace("\n", "").replace("\r", "")
100         return if (cleanHex.length % 2 == 0) {
101             (0 until cleanHex.length step 2)
102                 .map { cleanHex.substring(it, it + 2).toInt(16).toByte() }
!!!                                                             ^ error
103                 .toByteArray()
104         } else {
105             ByteArray(0) // Return empty if invalid hex

```

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/isotp/ISOTPProtocol.kt:40:19
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
37         try {
38             // Configure CAN for ISO-TP communication
39             sendRaw("ATSP6\r".toByteArray()) // Set protocol to ISO 15765-4
40             delay(100)
!!                   ^ error
41             
42             sendRaw("ATSTFF\r".toByteArray()) // Set timeout to maximum
43             delay(100)

```

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/isotp/ISOTPProtocol.kt:43:19
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
40             delay(100)
41             
42             sendRaw("ATSTFF\r".toByteArray()) // Set timeout to maximum
43             delay(100)
!!                   ^ error
44             
45             sendRaw("ATAT1\r".toByteArray()) // Enable auto timing
46             delay(100)

```

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/isotp/ISOTPProtocol.kt:46:19
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
43             delay(100)
44             
45             sendRaw("ATAT1\r".toByteArray()) // Enable auto timing
46             delay(100)
!!                   ^ error
47             
48             isInitialized.set(true)
49             completeInitialization()

```

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/isotp/ISOTPProtocol.kt:61:42
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
58     override suspend fun sendMessage(request: DiagnosticMessage): DiagnosticMessage {
59         return baseSendMessage(request, _config.responseTimeoutMs) { data ->
60             // Segment the message if it exceeds CAN frame size
61             val frames = if (data.size > 7) {
!!                                          ^ error
62                 segmenter.segment(data)
63             } else {
64                 listOf(data)

```

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/isotp/ISOTPProtocol.kt:79:42
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
76     ): DiagnosticMessage {
77         return baseSendMessage(request, timeoutMs) { data ->
78             // Segment the message if it exceeds CAN frame size
79             val frames = if (data.size > 7) {
!!                                          ^ error
80                 segmenter.segment(data)
81             } else {
82                 listOf(data)

```

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/isotp/ISOTPProtocol.kt:102:44
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
99          // ISO-TP doesn't typically use keep-alive, but we can send a tester present
100         if (isSessionActive) {
101             try {
102                 val request = buildRequest(0x3E, byteArrayOf(0x00)) // Tester Present
!!!                                            ^ error
103                 sendMessage(request, _config.responseTimeoutMs)
104             } catch (e: Exception) {
105                 // Keep-alive is best effort

```

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/isotp/ISOTPProtocol.kt:115:54
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
112         response: DiagnosticMessage
113     ): Boolean {
114         // For ISO-TP, responses follow standard UDS format with service ID + 0x40
115         val expectedServiceId = (request.serviceId + 0x40) and 0xFF
!!!                                                      ^ error
116         return response.serviceId == expectedServiceId
117     }
118     

```

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/isotp/ISOTPProtocol.kt:115:64
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
112         response: DiagnosticMessage
113     ): Boolean {
114         // For ISO-TP, responses follow standard UDS format with service ID + 0x40
115         val expectedServiceId = (request.serviceId + 0x40) and 0xFF
!!!                                                                ^ error
116         return response.serviceId == expectedServiceId
117     }
118     

```

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/isotp/ISOTPProtocol.kt:145:37
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
142         }
143         
144         // Check if this is a negative response (service ID = 0x7F)
145         if (response[0].toInt() and 0xFF == 0x7F) {
!!!                                     ^ error
146             if (response.size >= 3) {
147                 val requestedService = response[1].toInt() and 0xFF
148                 val nrc = response[2].toInt() and 0xFF

```

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/isotp/ISOTPProtocol.kt:145:45
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
142         }
143         
144         // Check if this is a negative response (service ID = 0x7F)
145         if (response[0].toInt() and 0xFF == 0x7F) {
!!!                                             ^ error
146             if (response.size >= 3) {
147                 val requestedService = response[1].toInt() and 0xFF
148                 val nrc = response[2].toInt() and 0xFF

```

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/isotp/ISOTPProtocol.kt:146:34
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
143         
144         // Check if this is a negative response (service ID = 0x7F)
145         if (response[0].toInt() and 0xFF == 0x7F) {
146             if (response.size >= 3) {
!!!                                  ^ error
147                 val requestedService = response[1].toInt() and 0xFF
148                 val nrc = response[2].toInt() and 0xFF
149                 return CANMessage(0x7F, response, true, nrc)

```

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/isotp/ISOTPProtocol.kt:147:64
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
144         // Check if this is a negative response (service ID = 0x7F)
145         if (response[0].toInt() and 0xFF == 0x7F) {
146             if (response.size >= 3) {
147                 val requestedService = response[1].toInt() and 0xFF
!!!                                                                ^ error
148                 val nrc = response[2].toInt() and 0xFF
149                 return CANMessage(0x7F, response, true, nrc)
150             } else {

```

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/isotp/ISOTPProtocol.kt:148:51
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
145         if (response[0].toInt() and 0xFF == 0x7F) {
146             if (response.size >= 3) {
147                 val requestedService = response[1].toInt() and 0xFF
148                 val nrc = response[2].toInt() and 0xFF
!!!                                                   ^ error
149                 return CANMessage(0x7F, response, true, nrc)
150             } else {
151                 throw ProtocolException("Invalid negative response format")

```

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/isotp/ISOTPProtocol.kt:149:35
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
146             if (response.size >= 3) {
147                 val requestedService = response[1].toInt() and 0xFF
148                 val nrc = response[2].toInt() and 0xFF
149                 return CANMessage(0x7F, response, true, nrc)
!!!                                   ^ error
150             } else {
151                 throw ProtocolException("Invalid negative response format")
152             }

```

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/isotp/ISOTPProtocol.kt:156:49
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
153         }
154         
155         // Positive response - service ID should be requested service + 0x40
156         val serviceId = response[0].toInt() and 0xFF
!!!                                                 ^ error
157         if (serviceId != (expectedService + 0x40) and 0xFF) {
158             throw ProtocolException("Invalid service ID in response")
159         }

```

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/isotp/ISOTPProtocol.kt:157:45
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
154         
155         // Positive response - service ID should be requested service + 0x40
156         val serviceId = response[0].toInt() and 0xFF
157         if (serviceId != (expectedService + 0x40) and 0xFF) {
!!!                                             ^ error
158             throw ProtocolException("Invalid service ID in response")
159         }
160         

```

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/isotp/ISOTPProtocol.kt:157:55
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
154         
155         // Positive response - service ID should be requested service + 0x40
156         val serviceId = response[0].toInt() and 0xFF
157         if (serviceId != (expectedService + 0x40) and 0xFF) {
!!!                                                       ^ error
158             throw ProtocolException("Invalid service ID in response")
159         }
160         

```

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/isotp/ISOTPProtocol.kt:167:44
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
164     override suspend fun performShutdownCleanup() {
165         try {
166             // Reset CAN adapter settings
167             sendRaw("ATZ\r".toByteArray(), 500)
!!!                                            ^ error
168         } catch (e: Exception) {
169             // Ignore errors during shutdown
170         }

```

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/isotp/ISOTPReassembler.kt:24:40
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
21     fun processFrame(frame: ByteArray): Boolean {
22         if (frame.isEmpty()) return false
23         
24         val pci = frame[0].toInt() and 0xFF
!!                                        ^ error
25         val frameType = (pci shr 4) and 0x0F
26         
27         when (frameType) {

```

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/isotp/ISOTPReassembler.kt:25:34
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
22         if (frame.isEmpty()) return false
23         
24         val pci = frame[0].toInt() and 0xFF
25         val frameType = (pci shr 4) and 0x0F
!!                                  ^ error
26         
27         when (frameType) {
28             0x00 -> { // Single frame

```

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/isotp/ISOTPReassembler.kt:37:34
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
34                 return true
35             }
36             0x01 -> { // First frame
37                 if (frame.size < 8) return false
!!                                  ^ error
38                 
39                 val upperLength = pci and 0x0F
40                 val lowerLength = frame[1].toInt() and 0xFF

```

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/isotp/ISOTPReassembler.kt:40:56
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
37                 if (frame.size < 8) return false
38                 
39                 val upperLength = pci and 0x0F
40                 val lowerLength = frame[1].toInt() and 0xFF
!!                                                        ^ error
41                 expectedLength = (upperLength shl 8) or lowerLength
42                 
43                 // Copy data from first frame (bytes 2-7)

```

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/isotp/ISOTPReassembler.kt:41:51
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
38                 
39                 val upperLength = pci and 0x0F
40                 val lowerLength = frame[1].toInt() and 0xFF
41                 expectedLength = (upperLength shl 8) or lowerLength
!!                                                   ^ error
42                 
43                 // Copy data from first frame (bytes 2-7)
44                 val dataFromFirstFrame = frame.sliceArray(2 until 8)

```

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/isotp/ISOTPReassembler.kt:44:67
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
41                 expectedLength = (upperLength shl 8) or lowerLength
42                 
43                 // Copy data from first frame (bytes 2-7)
44                 val dataFromFirstFrame = frame.sliceArray(2 until 8)
!!                                                                   ^ error
45                 receivedData = dataFromFirstFrame
46                 sequenceNumber = 1
47                 isReceiving = true

```

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/isotp/ISOTPReassembler.kt:75:57
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
72                 }
73                 
74                 // Increment sequence number
75                 sequenceNumber = (sequenceNumber + 1) % 16
!!                                                         ^ error
76                 return false
77             }
78             0x03 -> { // Flow control frame

```

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/isotp/ISOTPReassembler.kt:78:13
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
75                 sequenceNumber = (sequenceNumber + 1) % 16
76                 return false
77             }
78             0x03 -> { // Flow control frame
!!             ^ error
79                 // This is sent by receiver, not processed by reassembler
80                 return false
81             }

```

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/isotp/ISOTPSegmenter.kt:19:26
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
16     fun segment(data: ByteArray): List<ByteArray> {
17         val frames = mutableListOf<ByteArray>()
18         
19         if (data.size <= 7) {
!!                          ^ error
20             // Single frame - data fits in one CAN frame
21             val frame = ByteArray(data.size + 1)
22             frame[0] = (0x00 or data.size).toByte() // Single frame PCI

```

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/isotp/ISOTPSegmenter.kt:25:33
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
22             frame[0] = (0x00 or data.size).toByte() // Single frame PCI
23             System.arraycopy(data, 0, frame, 1, data.size)
24             frames.add(frame)
25         } else if (data.size <= 0xFFF) {
!!                                 ^ error
26             // First frame + consecutive frames
27             val totalLength = data.size
28             val firstFrameData = ByteArray(8)

```

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/isotp/ISOTPSegmenter.kt:28:44
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
25         } else if (data.size <= 0xFFF) {
26             // First frame + consecutive frames
27             val totalLength = data.size
28             val firstFrameData = ByteArray(8)
!!                                            ^ error
29             
30             // First frame PCI (0x10 = first frame, upper 4 bits = length upper bits)
31             val pci = (0x10 shl 4) or ((totalLength shr 8) and 0x0F)

```

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/isotp/ISOTPSegmenter.kt:31:24
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
28             val firstFrameData = ByteArray(8)
29             
30             // First frame PCI (0x10 = first frame, upper 4 bits = length upper bits)
31             val pci = (0x10 shl 4) or ((totalLength shr 8) and 0x0F)
!!                        ^ error
32             firstFrameData[0] = pci.toByte()
33             firstFrameData[1] = (totalLength and 0xFF).toByte() // Lower length bits
34             System.arraycopy(data, 0, firstFrameData, 2, 6) // First 6 bytes of data

```

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/isotp/ISOTPSegmenter.kt:31:33
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
28             val firstFrameData = ByteArray(8)
29             
30             // First frame PCI (0x10 = first frame, upper 4 bits = length upper bits)
31             val pci = (0x10 shl 4) or ((totalLength shr 8) and 0x0F)
!!                                 ^ error
32             firstFrameData[0] = pci.toByte()
33             firstFrameData[1] = (totalLength and 0xFF).toByte() // Lower length bits
34             System.arraycopy(data, 0, firstFrameData, 2, 6) // First 6 bytes of data

```

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/isotp/ISOTPSegmenter.kt:31:57
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
28             val firstFrameData = ByteArray(8)
29             
30             // First frame PCI (0x10 = first frame, upper 4 bits = length upper bits)
31             val pci = (0x10 shl 4) or ((totalLength shr 8) and 0x0F)
!!                                                         ^ error
32             firstFrameData[0] = pci.toByte()
33             firstFrameData[1] = (totalLength and 0xFF).toByte() // Lower length bits
34             System.arraycopy(data, 0, firstFrameData, 2, 6) // First 6 bytes of data

```

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/isotp/ISOTPSegmenter.kt:33:50
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
30             // First frame PCI (0x10 = first frame, upper 4 bits = length upper bits)
31             val pci = (0x10 shl 4) or ((totalLength shr 8) and 0x0F)
32             firstFrameData[0] = pci.toByte()
33             firstFrameData[1] = (totalLength and 0xFF).toByte() // Lower length bits
!!                                                  ^ error
34             System.arraycopy(data, 0, firstFrameData, 2, 6) // First 6 bytes of data
35             
36             frames.add(firstFrameData)

```

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/isotp/ISOTPSegmenter.kt:34:58
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
31             val pci = (0x10 shl 4) or ((totalLength shr 8) and 0x0F)
32             firstFrameData[0] = pci.toByte()
33             firstFrameData[1] = (totalLength and 0xFF).toByte() // Lower length bits
34             System.arraycopy(data, 0, firstFrameData, 2, 6) // First 6 bytes of data
!!                                                          ^ error
35             
36             frames.add(firstFrameData)
37             

```

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/isotp/ISOTPSegmenter.kt:39:26
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
36             frames.add(firstFrameData)
37             
38             // Consecutive frames
39             var offset = 6
!!                          ^ error
40             var sequenceNumber = 1
41             while (offset < totalLength) {
42                 val remaining = totalLength - offset

```

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/isotp/ISOTPSegmenter.kt:43:39
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
40             var sequenceNumber = 1
41             while (offset < totalLength) {
42                 val remaining = totalLength - offset
43                 val frameSize = minOf(7, remaining) // Max 7 bytes per consecutive frame
!!                                       ^ error
44                 val cfData = ByteArray(8)
45                 
46                 // Consecutive frame PCI (0x20 + sequence number)

```

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/isotp/ISOTPSegmenter.kt:44:40
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
41             while (offset < totalLength) {
42                 val remaining = totalLength - offset
43                 val frameSize = minOf(7, remaining) // Max 7 bytes per consecutive frame
44                 val cfData = ByteArray(8)
!!                                        ^ error
45                 
46                 // Consecutive frame PCI (0x20 + sequence number)
47                 cfData[0] = ((0x20 or (sequenceNumber and 0x0F)) and 0xFF).toByte()

```

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/isotp/ISOTPSegmenter.kt:47:31
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
44                 val cfData = ByteArray(8)
45                 
46                 // Consecutive frame PCI (0x20 + sequence number)
47                 cfData[0] = ((0x20 or (sequenceNumber and 0x0F)) and 0xFF).toByte()
!!                               ^ error
48                 System.arraycopy(data, offset, cfData, 1, frameSize)
49                 
50                 frames.add(cfData)

```

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/isotp/ISOTPSegmenter.kt:47:70
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
44                 val cfData = ByteArray(8)
45                 
46                 // Consecutive frame PCI (0x20 + sequence number)
47                 cfData[0] = ((0x20 or (sequenceNumber and 0x0F)) and 0xFF).toByte()
!!                                                                      ^ error
48                 System.arraycopy(data, offset, cfData, 1, frameSize)
49                 
50                 frames.add(cfData)

```

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/isotp/ISOTPSegmenter.kt:53:57
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
50                 frames.add(cfData)
51                 
52                 offset += frameSize
53                 sequenceNumber = (sequenceNumber + 1) % 16
!!                                                         ^ error
54             }
55         } else {
56             // Extended addressing for messages > 4095 bytes

```

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/isotp/ISOTPSegmenter.kt:58:44
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
55         } else {
56             // Extended addressing for messages > 4095 bytes
57             val totalLength = data.size
58             val firstFrameData = ByteArray(8)
!!                                            ^ error
59             
60             // First frame PCI with extended addressing (0x10 + length in next 4 bytes)
61             firstFrameData[0] = 0x10.toByte() // First frame

```

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/isotp/ISOTPSegmenter.kt:63:51
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
60             // First frame PCI with extended addressing (0x10 + length in next 4 bytes)
61             firstFrameData[0] = 0x10.toByte() // First frame
62             firstFrameData[1] = 0x00.toByte() // Extended address (0 for now)
63             firstFrameData[2] = ((totalLength shr 24) and 0xFF).toByte() // Length bytes
!!                                                   ^ error
64             firstFrameData[3] = ((totalLength shr 16) and 0xFF).toByte()
65             firstFrameData[4] = ((totalLength shr 8) and 0xFF).toByte()
66             firstFrameData[5] = (totalLength and 0xFF).toByte()

```

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/isotp/ISOTPSegmenter.kt:63:59
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
60             // First frame PCI with extended addressing (0x10 + length in next 4 bytes)
61             firstFrameData[0] = 0x10.toByte() // First frame
62             firstFrameData[1] = 0x00.toByte() // Extended address (0 for now)
63             firstFrameData[2] = ((totalLength shr 24) and 0xFF).toByte() // Length bytes
!!                                                           ^ error
64             firstFrameData[3] = ((totalLength shr 16) and 0xFF).toByte()
65             firstFrameData[4] = ((totalLength shr 8) and 0xFF).toByte()
66             firstFrameData[5] = (totalLength and 0xFF).toByte()

```

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/isotp/ISOTPSegmenter.kt:64:28
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
61             firstFrameData[0] = 0x10.toByte() // First frame
62             firstFrameData[1] = 0x00.toByte() // Extended address (0 for now)
63             firstFrameData[2] = ((totalLength shr 24) and 0xFF).toByte() // Length bytes
64             firstFrameData[3] = ((totalLength shr 16) and 0xFF).toByte()
!!                            ^ error
65             firstFrameData[4] = ((totalLength shr 8) and 0xFF).toByte()
66             firstFrameData[5] = (totalLength and 0xFF).toByte()
67             

```

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/isotp/ISOTPSegmenter.kt:64:51
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
61             firstFrameData[0] = 0x10.toByte() // First frame
62             firstFrameData[1] = 0x00.toByte() // Extended address (0 for now)
63             firstFrameData[2] = ((totalLength shr 24) and 0xFF).toByte() // Length bytes
64             firstFrameData[3] = ((totalLength shr 16) and 0xFF).toByte()
!!                                                   ^ error
65             firstFrameData[4] = ((totalLength shr 8) and 0xFF).toByte()
66             firstFrameData[5] = (totalLength and 0xFF).toByte()
67             

```

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/isotp/ISOTPSegmenter.kt:64:59
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
61             firstFrameData[0] = 0x10.toByte() // First frame
62             firstFrameData[1] = 0x00.toByte() // Extended address (0 for now)
63             firstFrameData[2] = ((totalLength shr 24) and 0xFF).toByte() // Length bytes
64             firstFrameData[3] = ((totalLength shr 16) and 0xFF).toByte()
!!                                                           ^ error
65             firstFrameData[4] = ((totalLength shr 8) and 0xFF).toByte()
66             firstFrameData[5] = (totalLength and 0xFF).toByte()
67             

```

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/isotp/ISOTPSegmenter.kt:65:28
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
62             firstFrameData[1] = 0x00.toByte() // Extended address (0 for now)
63             firstFrameData[2] = ((totalLength shr 24) and 0xFF).toByte() // Length bytes
64             firstFrameData[3] = ((totalLength shr 16) and 0xFF).toByte()
65             firstFrameData[4] = ((totalLength shr 8) and 0xFF).toByte()
!!                            ^ error
66             firstFrameData[5] = (totalLength and 0xFF).toByte()
67             
68             // Fill with remaining data

```

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/isotp/ISOTPSegmenter.kt:65:51
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
62             firstFrameData[1] = 0x00.toByte() // Extended address (0 for now)
63             firstFrameData[2] = ((totalLength shr 24) and 0xFF).toByte() // Length bytes
64             firstFrameData[3] = ((totalLength shr 16) and 0xFF).toByte()
65             firstFrameData[4] = ((totalLength shr 8) and 0xFF).toByte()
!!                                                   ^ error
66             firstFrameData[5] = (totalLength and 0xFF).toByte()
67             
68             // Fill with remaining data

```

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/isotp/ISOTPSegmenter.kt:65:58
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
62             firstFrameData[1] = 0x00.toByte() // Extended address (0 for now)
63             firstFrameData[2] = ((totalLength shr 24) and 0xFF).toByte() // Length bytes
64             firstFrameData[3] = ((totalLength shr 16) and 0xFF).toByte()
65             firstFrameData[4] = ((totalLength shr 8) and 0xFF).toByte()
!!                                                          ^ error
66             firstFrameData[5] = (totalLength and 0xFF).toByte()
67             
68             // Fill with remaining data

```

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/isotp/ISOTPSegmenter.kt:66:28
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
63             firstFrameData[2] = ((totalLength shr 24) and 0xFF).toByte() // Length bytes
64             firstFrameData[3] = ((totalLength shr 16) and 0xFF).toByte()
65             firstFrameData[4] = ((totalLength shr 8) and 0xFF).toByte()
66             firstFrameData[5] = (totalLength and 0xFF).toByte()
!!                            ^ error
67             
68             // Fill with remaining data
69             val dataBytesAvailable = minOf(3, totalLength) // Only 3 bytes available after length

```

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/isotp/ISOTPSegmenter.kt:66:50
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
63             firstFrameData[2] = ((totalLength shr 24) and 0xFF).toByte() // Length bytes
64             firstFrameData[3] = ((totalLength shr 16) and 0xFF).toByte()
65             firstFrameData[4] = ((totalLength shr 8) and 0xFF).toByte()
66             firstFrameData[5] = (totalLength and 0xFF).toByte()
!!                                                  ^ error
67             
68             // Fill with remaining data
69             val dataBytesAvailable = minOf(3, totalLength) // Only 3 bytes available after length

```

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/isotp/ISOTPSegmenter.kt:69:44
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
66             firstFrameData[5] = (totalLength and 0xFF).toByte()
67             
68             // Fill with remaining data
69             val dataBytesAvailable = minOf(3, totalLength) // Only 3 bytes available after length
!!                                            ^ error
70             System.arraycopy(data, 0, firstFrameData, 5, dataBytesAvailable)
71             
72             frames.add(firstFrameData)

```

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/isotp/ISOTPSegmenter.kt:70:55
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
67             
68             // Fill with remaining data
69             val dataBytesAvailable = minOf(3, totalLength) // Only 3 bytes available after length
70             System.arraycopy(data, 0, firstFrameData, 5, dataBytesAvailable)
!!                                                       ^ error
71             
72             frames.add(firstFrameData)
73             

```

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/isotp/ISOTPSegmenter.kt:79:39
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
76             var sequenceNumber = 1
77             while (offset < totalLength) {
78                 val remaining = totalLength - offset
79                 val frameSize = minOf(7, remaining)
!!                                       ^ error
80                 val cfData = ByteArray(8)
81                 
82                 cfData[0] = ((0x20 or (sequenceNumber and 0x0F)) and 0xFF).toByte()

```

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/isotp/ISOTPSegmenter.kt:80:40
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
77             while (offset < totalLength) {
78                 val remaining = totalLength - offset
79                 val frameSize = minOf(7, remaining)
80                 val cfData = ByteArray(8)
!!                                        ^ error
81                 
82                 cfData[0] = ((0x20 or (sequenceNumber and 0x0F)) and 0xFF).toByte()
83                 System.arraycopy(data, offset, cfData, 1, frameSize)

```

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/isotp/ISOTPSegmenter.kt:82:31
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
79                 val frameSize = minOf(7, remaining)
80                 val cfData = ByteArray(8)
81                 
82                 cfData[0] = ((0x20 or (sequenceNumber and 0x0F)) and 0xFF).toByte()
!!                               ^ error
83                 System.arraycopy(data, offset, cfData, 1, frameSize)
84                 
85                 frames.add(cfData)

```

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/isotp/ISOTPSegmenter.kt:82:70
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
79                 val frameSize = minOf(7, remaining)
80                 val cfData = ByteArray(8)
81                 
82                 cfData[0] = ((0x20 or (sequenceNumber and 0x0F)) and 0xFF).toByte()
!!                                                                      ^ error
83                 System.arraycopy(data, offset, cfData, 1, frameSize)
84                 
85                 frames.add(cfData)

```

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/isotp/ISOTPSegmenter.kt:88:57
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
85                 frames.add(cfData)
86                 
87                 offset += frameSize
88                 sequenceNumber = (sequenceNumber + 1) % 16
!!                                                         ^ error
89             }
90         }
91         

```

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/isotp/ISOTPSegmenter.kt:102:31
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
99       * @param separationTime Separation time (0x00-0x7F: 0-127ms, 0xF1-0xF9: 100-900us)
100      */
101     fun createFlowControlFrame(flowStatus: Int, blockSize: Int, separationTime: Int): ByteArray {
102         val frame = ByteArray(8)
!!!                               ^ error
103         frame[0] = 0x30.toByte() // Flow control PCI
104         frame[1] = flowStatus.toByte()
105         frame[2] = blockSize.toByte()

```

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/isotp/ISOTPSegmenter.kt:106:15
```
This expression contains a magic number. Consider defining it to a well named constant.
```
```kotlin
103         frame[0] = 0x30.toByte() // Flow control PCI
104         frame[1] = flowStatus.toByte()
105         frame[2] = blockSize.toByte()
106         frame[3] = separationTime.toByte()
!!!               ^ error
107         // Remaining bytes are padding
108         return frame
109     }

```

### style, MaxLineLength (2)

Line detected, which is longer than the defined maximum line length in the code style.

[Documentation](https://detekt.dev/docs/rules/style#maxlinelength)

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/CANFrame.kt:30:1
```
Line detected, which is longer than the defined maximum line length in the code style.
```
```kotlin
27     override fun getType(): FrameType = if (isExtendedFrame) FrameType.EXTENDED_CAN else FrameType.STANDARD_CAN
28     
29     override fun toString(): String {
30         return "CANFrame(canId=0x${canId.toString(16)}, data=[${data.joinToString(", ") { String.format("0x%02X", it) }}], extended=${isExtendedFrame})"
!! ^ error
31     }
32     
33     companion object {

```

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/CANMessage.kt:22:1
```
Line detected, which is longer than the defined maximum line length in the code style.
```
```kotlin
19     override fun toByteArray(): ByteArray = data
20     
21     override fun toString(): String {
22         return "CANMessage(serviceId=0x${serviceId.toString(16)}, data=${data.joinToString(" ") { String.format("%02X", it) }}, isNegativeResponse=$isNegativeResponse, nrc=${if (isNegativeResponse) String.format("0x%02X", negativeResponseCode) else "N/A"})"
!! ^ error
23     }
24 }

```

### style, NewLineAtEndOfFile (7)

Checks whether files end with a line separator.

[Documentation](https://detekt.dev/docs/rules/style#newlineatendoffile)

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/CANFilter.kt:49:2
```
The file /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/CANFilter.kt is not ending with a new line.
```
```kotlin
46             return CANFilter(baseId, rangeMask, isExtended)
47         }
48     }
49 }
!!  ^ error

```

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/CANFrame.kt:49:2
```
The file /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/CANFrame.kt is not ending with a new line.
```
```kotlin
46             }
47         }
48     }
49 }
!!  ^ error

```

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/CANMessage.kt:24:2
```
The file /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/CANMessage.kt is not ending with a new line.
```
```kotlin
21     override fun toString(): String {
22         return "CANMessage(serviceId=0x${serviceId.toString(16)}, data=${data.joinToString(" ") { String.format("%02X", it) }}, isNegativeResponse=$isNegativeResponse, nrc=${if (isNegativeResponse) String.format("0x%02X", negativeResponseCode) else "N/A"})"
23     }
24 }
!!  ^ error

```

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/isotp/ISOTPFrameHandler.kt:125:2
```
The file /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/isotp/ISOTPFrameHandler.kt is not ending with a new line.
```
```kotlin
122         val flowControlFrame = segmenter.createFlowControlFrame(flowStatus, blockSize, separationTime)
123         connection.sendCommand(bytesToHex(flowControlFrame))
124     }
125 }
!!!  ^ error

```

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/isotp/ISOTPProtocol.kt:173:2
```
The file /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/isotp/ISOTPProtocol.kt is not ending with a new line.
```
```kotlin
170         }
171         isInitialized.set(false)
172     }
173 }
!!!  ^ error

```

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/isotp/ISOTPReassembler.kt:125:2
```
The file /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/isotp/ISOTPReassembler.kt is not ending with a new line.
```
```kotlin
122         
123         return null // Message not complete
124     }
125 }
!!!  ^ error

```

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/isotp/ISOTPSegmenter.kt:110:2
```
The file /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/isotp/ISOTPSegmenter.kt is not ending with a new line.
```
```kotlin
107         // Remaining bytes are padding
108         return frame
109     }
110 }
!!!  ^ error

```

### style, ReturnCount (2)

Restrict the number of return statements in methods.

[Documentation](https://detekt.dev/docs/rules/style#returncount)

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/isotp/ISOTPFrameHandler.kt:20:17
```
Function sendMultiFrameMessage has 3 return statements which exceeds the limit of 2.
```
```kotlin
17      * @param frames The frames to send
18      * @return The response as a complete message
19      */
20     suspend fun sendMultiFrameMessage(connection: ScannerConnection, frames: List<ByteArray>): ByteArray {
!!                 ^ error
21         if (frames.isEmpty()) return ByteArray(0)
22         
23         // If only one frame, send directly

```

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/isotp/ISOTPReassembler.kt:21:9
```
Function processFrame has 11 return statements which exceeds the limit of 2.
```
```kotlin
18      * @param frame The incoming CAN frame
19      * @return True if the message is complete, false otherwise
20      */
21     fun processFrame(frame: ByteArray): Boolean {
!!         ^ error
22         if (frame.isEmpty()) return false
23         
24         val pci = frame[0].toInt() and 0xFF

```

### style, UnusedPrivateProperty (4)

Property is unused and should be removed.

[Documentation](https://detekt.dev/docs/rules/style#unusedprivateproperty)

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/isotp/ISOTPFrameHandler.kt:12:17
```
Private property `reassembler` is unused.
```
```kotlin
9  class ISOTPFrameHandler {
10     
11     private val segmenter = ISOTPSegmenter()
12     private val reassembler = ISOTPReassembler()
!!                 ^ error
13     
14     /**
15      * Sends a multi-frame message with proper flow control

```

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/isotp/ISOTPFrameHandler.kt:58:17
```
Private property `cfResponse` is unused.
```
```kotlin
55                 delay(100)
56             }
57             
58             val cfResponse = connection.sendCommand(bytesToHex(frames[i]))
!!                 ^ error
59             
60             if (separationTime > 0) {
61                 if (separationTime <= 0x7F) {

```

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/isotp/ISOTPProtocol.kt:28:17
```
Private property `reassembler` is unused.
```
```kotlin
25     
26     private val isInitialized = AtomicBoolean(false)
27     private val segmenter = ISOTPSegmenter()
28     private val reassembler = ISOTPReassembler()
!!                 ^ error
29     private val frameHandler = ISOTPFrameHandler()
30     
31     override suspend fun initialize(

```

* /home/yungblud/Desktop/SpaceTec/protocol/can/src/main/java/com/spacetec/protocol/can/isotp/ISOTPProtocol.kt:147:21
```
Private property `requestedService` is unused.
```
```kotlin
144         // Check if this is a negative response (service ID = 0x7F)
145         if (response[0].toInt() and 0xFF == 0x7F) {
146             if (response.size >= 3) {
147                 val requestedService = response[1].toInt() and 0xFF
!!!                     ^ error
148                 val nrc = response[2].toInt() and 0xFF
149                 return CANMessage(0x7F, response, true, nrc)
150             } else {

```

generated with [detekt version 1.23.4](https://detekt.dev/) on 2025-12-25 23:00:09 UTC

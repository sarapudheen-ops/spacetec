# J2534 PassThru API Implementation for Android

This project provides a complete implementation of the SAE J2534 standard for vehicle reprogramming interfaces on Android. It includes robust, thread-safe implementations with JNI/NDK integration, multi-protocol support, and ECU flashing capabilities.

## Features

- **Complete J2534 Implementation**: All 17 PassThru API functions as defined in SAE J2534-1 v04.04
- **Multi-Protocol Support**: CAN, ISO15765 (ISO-TP), J1850, ISO9141, ISO14230
- **Thread-Safe Architecture**: Using ConcurrentHashMap and ReentrantReadWriteLock for safe concurrent access
- **UDS Support**: Unified Diagnostic Services with ISO-TP transport protocol
- **ECU Programming**: Complete implementation for ECU flashing and reprogramming
- **USB OTG Communication**: Direct USB communication with J2534-compliant devices
- **Resource Management**: Automatic cleanup of channels, filters, and periodic messages
- **Error Handling**: Comprehensive error handling following J2534 standards

## Architecture

### Core Components

1. **J2534Manager**: Main entry point managing devices, channels, and providing thread-safe access
2. **J2534Device**: Represents connected J2534 devices with USB communication
3. **J2534Channel**: Communication channels with protocol-specific functionality
4. **J2534Session**: ECU communication sessions with UDS support
5. **J2534JniWrapper**: JNI interface to native C++ implementation

### Package Structure

```
j2534/
├── src/main/java/com/spacetec/j2534/
│   ├── J2534Manager.kt          # Main manager class
│   ├── J2534Device.kt           # Device representation
│   ├── J2534Channel.kt          # Channel management
│   ├── J2534Session.kt          # Communication sessions
│   ├── api/                     # PassThru API functions
│   │   └── PassThruApi.kt
│   ├── constants/               # J2534 constants
│   │   └── J2534Errors.kt
│   ├── message/                 # Message handling
│   │   └── MessageUtils.kt
│   ├── config/                  # Configuration classes
│   │   └── J2534Config.kt
│   ├── wrapper/                 # JNI wrappers
│   ├── device/                  # Device communication
│   │   └── UsbJ2534Device.kt
│   └── example/                 # Usage examples
│       └── J2534Example.kt
└── src/main/jni/                # Native implementation
    ├── j2534_jni.cpp
    ├── j2534_jni.h
    └── Android.mk
```

## Usage

### Basic Usage

```kotlin
// Initialize the manager
val manager = J2534Manager.getInstance()
if (!manager.initialize()) {
    // Handle initialization failure
    return
}

// Scan for devices
val devices = manager.scanForDevices()
if (devices.isEmpty()) {
    // No devices found
    return
}

// Connect to a device
val device = devices[0]
val channelHandle = manager.connect(
    device,
    J2534Protocols.ISO15765,  // ISO-TP protocol
    0L,                        // Flags
    500000L                    // Baudrate
)

// Create a channel for easier management
val channel = J2534Channel(channelHandle, manager, device, J2534Protocols.ISO15765)

// Create and start a session
val session = J2534Session.createSession(channel)
session.start()

// Send a UDS request
val response = session.sendDiagnosticRequest(0x22.toByte(), byteArrayOf(0xF1, 0x90)) // Read VIN

// Clean up
session.stop()
channel.close()
manager.cleanup()
```

### UDS Communication

```kotlin
// Create a session for UDS communication
val session = J2534Session.createSession(channel, J2534Protocols.ISO15765)

// Start with specific configuration
val config = J2534Session.SessionConfig(
    blockSize = 8,
    stMin = 0,
    flowControlTimeout = 2000L
)
session.start(config)

// Read ECU data
val vin = session.readEcuData(0xF190.toShort()) // VIN data identifier

// Perform ECU programming
val programmingData = ByteArray(1024) // Your firmware data
val success = session.performEcuProgramming(programmingData)

// Stop the session
session.stop()
```

### Message Filtering

```kotlin
// Create a mask and pattern for filtering
val mask = J2534Message().apply {
    protocolID = J2534Protocols.ISO15765
    data = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())
}

val pattern = J2534Message().apply {
    protocolID = J2534Protocols.ISO15765
    data = byteArrayOf(0x00, 0x00, 0x07.toByte(), 0xE0.toByte(), 0x00) // ECU address 0x7E0
}

// Start a pass filter
val filterId = channel.startMessageFilter(J2534FilterTypes.PASS_FILTER, mask, pattern, null)

// Read only filtered messages
val messages = Array(10) { J2534Message() }
channel.readMessages(messages, 10, 2000L)

// Stop the filter
channel.stopMessageFilter(filterId)
```

## Supported J2534 Devices

The implementation supports common J2534-compliant devices:
- Tactrix OpenPort
- Drew Technologies devices
- OBDLink devices
- Other FTDI-based J2534 interfaces

## ECU Programming Support

The implementation includes complete support for ECU reprogramming:
- Request download sequence
- Transfer data in blocks
- Request transfer exit
- Routine control for programming
- Verification after write
- Programming voltage control

## Error Handling

All J2534 error codes are properly implemented:
- STATUS_NOERROR
- ERR_NOT_SUPPORTED
- ERR_INVALID_CHANNEL_ID
- ERR_INVALID_PROTOCOL_ID
- ERR_TIMEOUT
- ERR_BUFFER_OVERFLOW
- And all other standard J2534 error codes

## Safety Features

- Programming voltage safety checks
- Battery voltage monitoring during flashing
- Automatic resource cleanup
- Timeout handling for all operations
- Concurrent channel support

## Building

To build the native components, ensure you have the Android NDK installed and use:

```bash
ndk-build -C j2534/src/main/jni
```

Or integrate with your existing Gradle build system.

## License

This implementation follows automotive industry standards and best practices for vehicle communication.
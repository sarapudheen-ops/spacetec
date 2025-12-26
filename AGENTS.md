# AGENTS.md

This file provides guidance to Qoder (qoder.com) when working with code in this repository.

## Project Overview

SpaceTec is a professional-grade Android application for vehicle diagnostic trouble code (DTC) management. The system supports multiple scanner types (Bluetooth, WiFi, USB, J2534) and various automotive protocols (OBD-II, UDS, CAN, K-Line, J1850) for professional mechanics and automotive technicians. The application follows clean architecture principles with a multi-module structure.

## Architecture Structure

The project implements a multi-layered clean architecture:

### Module Structure
1. **App Module** (`:app`) - Main application entry point with Jetpack Compose UI
2. **Core Modules** - Shared infrastructure:
   - `:core:common` - Common utilities and extensions
   - `:core:database` - Room database with DAOs and FTS4 search
   - `:core:network` - Retrofit networking implementation
   - `:core:datastore` - Data persistence layer
   - `:core:security` - Encryption and security implementations
   - `:core:logging` - Comprehensive logging framework
   - `:core:domain` - Domain entities and use cases
   - `:core:ui` - Shared UI components and composables
   - `:core:testing` - Testing utilities and frameworks
3. **Protocol Modules** - Vehicle communication protocols:
   - `:protocol:core` - Protocol base classes and interfaces
   - `:protocol:obd` - OBD-II protocol implementation (Services 01-0A)
   - `:protocol:uds` - Unified Diagnostic Services implementation
   - `:protocol:can` - CAN bus protocol implementation
   - `:protocol:kline` - K-Line protocol implementation (ISO 9141, ISO 14230)
   - `:protocol:j1850` - J1850 protocol implementation (VPW, PWM)
   - `:protocol:iso9141` - ISO 9141 protocol implementation
   - `:protocol:iso14230` - ISO 14230 protocol implementation
   - `:protocol:ethernet` - Ethernet-based diagnostic protocols
4. **Scanner Modules** - Hardware integration (currently commented out due to AGP 8.x circular dependency bug):
   - `:scanner:core` - Scanner base classes and interfaces
   - `:scanner:devices` - Scanner device implementations
   - `:scanner:bluetooth` - Bluetooth scanner connectivity
   - `:scanner:wifi` - WiFi scanner connectivity
   - `:scanner:usb` - USB scanner connectivity
   - `:scanner:j2534` - J2534 Pass-Thru device support
5. **Feature Modules** - Specific functionalities:
   - `:features:dtc` - DTC reading, clearing, and analysis
   - `:features:livedata` - Live data monitoring and PID reading
   - `:features:freezeframe` - Freeze frame data capture and analysis
   - `:features:reports` - Diagnostic report generation
   - `:features:coding` - Vehicle coding operations
   - `:features:bidirectional` - Bidirectional communication features
   - `:features:keyprogramming` - Key programming operations
   - `:features:ecu` - ECU operations and programming
   - `:features:maintenance` - Maintenance operations
   - `:features:dashboard` - Main dashboard and overview
   - `:features:settings` - Application settings and configuration
   - `:features:connection` - Connection management UI
   - `:features:vehicleinfo` - Vehicle information display
6. **Vehicle Modules** - Brand-specific implementations:
   - `:vehicle:core` - Generic vehicle functionality
   - Brand-specific modules under `:vehicle:brands` supporting Audi, BMW, Mercedes, VW, Toyota, Ford, Honda, Nissan, Hyundai, Kia, Porsche, Jaguar, Land Rover, Volvo, Mazda, Subaru, Lexus, Chevrolet, GM, Chrysler, Dodge, Jeep, and generic implementations
7. **Analysis Modules** - Intelligent diagnostic analysis:
   - `:analysis:core` - Core analysis algorithms
   - `:analysis:ml` - Machine learning for diagnostic predictions
   - `:analysis:patterns` - Pattern recognition for DTC correlations
   - `:analysis:predictions` - Predictive diagnostic capabilities
8. **Compliance Modules** - Regulatory compliance implementations:
   - `:compliance:core` - Core compliance framework
   - `:compliance:carb` - California Air Resources Board compliance
   - `:compliance:euro6` - European Euro 6 emissions standards
   - `:compliance:china6` - China 6 emissions standards
   - `:compliance:india-bs6` - India BS6 emissions standards
   - `:compliance:emissions` - General emissions compliance

## Key Domain Entities

### DTC Entity
The core DTC data class includes:
- Code, description, explanation, and system classification
- Severity (CRITICAL, HIGH, MEDIUM, LOW, INFORMATIONAL)
- Status tracking (testFailed, confirmedDTC, pendingDTC, etc.)
- Freeze frame data and related DTCs
- Possible causes, symptoms, and diagnostic steps
- Technical bulletins and repair cost estimates

### Protocol Support
- OBD-II Services 01-0A implementation with PID registry
- UDS Service 0x19 (Read DTC Information) with all sub-functions
- Brand-specific protocol extensions (VAG: KWP1281, KWP2000; BMW: DS2; Mercedes: Xentry format)
- Multi-ECU parallel scanning capabilities
- CAN, K-Line, and J1850 protocol implementations
- ISO 9141 and ISO 14230 implementations

## Common Development Commands

### Build Commands
```bash
./gradlew assembleDebug          # Build debug APK
./gradlew assembleRelease        # Build release APK
./gradlew build                  # Build all variants
./gradlew :app:assembleDebug     # Build specific module
```

### Test Commands
```bash
./gradlew test                   # Run all unit tests
./gradlew testDebugUnitTest      # Run unit tests for debug variant
./gradlew connectedAndroidTest   # Run instrumentation tests
./gradlew :core:testing:unit:test     # Run unit tests for testing module
./gradlew :features:dtc:test     # Run tests for specific feature module
```

### Lint and Analysis Commands
```bash
./gradlew lint                   # Run lint on all modules
./gradlew detekt                 # Run detekt static analysis
./gradlew ktlintCheck            # Run ktlint formatting check
./gradlew ktlintFormat           # Apply ktlint formatting
./gradlew check                  # Run all checks (lint, detekt, tests)
```

### Database and Protocol Commands
```bash
./gradlew :core:database:generateDebugDatabaseSchema    # Generate database schema
./gradlew :protocol:obd:compileDebugKotlin              # Build protocol module
./gradlew :features:dtc:compileDebugKotlin             # Build DTC feature module
```

### Development Workflow Commands
```bash
./gradlew projectInfo              # Display project module information
./gradlew runAllTests             # Run all unit tests across all modules
./gradlew runAllLintChecks        # Run all lint checks across all modules
./gradlew runAllDetektChecks      # Run detekt analysis on all modules
./gradlew generateModuleGraph     # Generate module dependency graph
./gradlew generateAllDocumentation # Generate documentation for all modules
./gradlew checkDependencyUpdates  # Check for available dependency updates
```

## Key Technologies and Libraries

- **Kotlin 1.9.x** with Coroutines and Flow for asynchronous operations
- **Android Jetpack Compose** for modern UI development
- **Hilt** for dependency injection
- **Room** with FTS4 for local database and fast text search
- **Retrofit** for network communication
- **Kotlin Flow** for reactive programming
- **AndroidX libraries** for modern Android development
- **Gradle Kotlin DSL** for build configuration
- **KSP (Kotlin Symbol Processing)** for annotation processing

## Specialized Development Tasks

### Working with DTC Database
- The system maintains an offline database with 50,000+ DTC definitions
- Uses FTS4 for fast text search capabilities (<50ms response)
- Supports 10+ languages for DTC descriptions
- Includes manufacturer-specific DTC databases

### Scanner Connectivity Implementation
- Multi-transport architecture supporting Bluetooth, WiFi, USB, and J2534
- Common scanner interface abstraction across all transport types
- Connection state management with error handling and recovery
- Device discovery and pairing management
- NOTE: Scanner modules are currently disabled due to AGP 8.x circular dependency bug

### Protocol Implementation Guidelines
- All protocol modules extend the `:protocol:core` base classes
- Implement standardized service handlers for OBD-II services
- Support both standard and manufacturer-specific extensions
- Include proper error handling and protocol-specific initialization sequences

### Brand-Specific Customization
- Each brand module implements vehicle-specific protocols and procedures
- Custom ECU addressing and initialization sequences
- Brand-specific DTC formats and interpretations
- Integration with manufacturer diagnostic systems (ISTA-D, Xentry, etc.)

### Analysis and Machine Learning
- ML-based diagnostic pattern recognition
- Predictive maintenance algorithms
- DTC correlation analysis
- Performance optimization for real-time analysis

## Performance and Offline Considerations

- Full offline functionality with comprehensive local database
- Optimized for <60 second full system scans
- DTC lookup performance target <50ms
- Efficient memory usage and battery consumption management
- Background service optimization for continuous monitoring
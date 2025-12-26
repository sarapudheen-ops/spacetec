# Scanner Connection State Management System

## Overview

This package implements comprehensive state management for the scanner connection system as specified in task 12.2. It provides unified state management across all connection types with state synchronization, consistency checking, and persistence.

## Components

### ConnectionStateManager
Central state manager that coordinates all connection state operations:
- **Connection Registration**: Tracks all active connections
- **State Synchronization**: Monitors and synchronizes state changes across connections
- **State Persistence**: Saves and recovers state from local storage
- **Performance Monitoring**: Tracks connection quality metrics
- **Recovery Management**: Handles automatic recovery for failed connections

### StateSynchronizer
Manages state consistency and cross-connection coordination:
- **Conflict Resolution**: Resolves conflicts when multiple connections exist to the same scanner
- **Priority Management**: Enforces connection priority rules (J2534 > USB > WiFi > Bluetooth)
- **Resource Management**: Prevents resource exhaustion by limiting concurrent connections
- **Quality-Based Switching**: Automatically switches to better quality connections
- **Consistency Checks**: Validates state consistency across the system

### ConnectionStateRepository
Handles state persistence to local storage:
- **Connection State Persistence**: Saves individual connection states
- **Global State Persistence**: Saves overall system state
- **State Recovery**: Loads persisted state on app restart
- **Cleanup**: Removes stale state data

### EnhancedScannerManager
Enhanced wrapper around the base ScannerManager:
- **Integrated State Management**: Combines base scanner functionality with enhanced state management
- **Enhanced State Flows**: Provides additional state information including global connection health
- **State Events**: Exposes state management and synchronization events
- **Recovery API**: Provides methods to trigger recovery and synchronization

## Key Features

### 1. Unified State Management
- Single source of truth for all connection states
- Consistent state representation across connection types
- Real-time state updates via Kotlin Flows

### 2. State Synchronization
- Automatic synchronization of state changes
- Cross-connection state coordination
- Conflict detection and resolution
- Resource constraint enforcement

### 3. State Persistence
- Automatic state saving to local storage
- State recovery on app restart
- Graceful handling of corrupted state data
- Efficient JSON-based serialization

### 4. Connection Quality Monitoring
- Real-time quality metrics tracking
- Quality-based connection switching
- Performance degradation alerts
- Health scoring system

### 5. Recovery Management
- Automatic recovery for recoverable errors
- Exponential backoff for reconnection attempts
- Backup connection activation
- Recovery attempt tracking

## Usage

### Basic Integration

```kotlin
@Inject
lateinit var enhancedScannerManager: EnhancedScannerManager

// Observe enhanced scanner state
lifecycleScope.launch {
    enhancedScannerManager.enhancedState.collect { state ->
        updateUI(state)
    }
}

// Observe global connection state
lifecycleScope.launch {
    enhancedScannerManager.globalConnectionState.collect { globalState ->
        updateConnectionHealth(globalState.overallHealth)
    }
}

// Connect to scanner (state management is automatic)
val result = enhancedScannerManager.connect(scanner)
```

### Advanced Features

```kotlin
// Monitor state events
lifecycleScope.launch {
    enhancedScannerManager.stateEvents.collect { event ->
        when (event) {
            is StateEvent.ConnectionError -> handleError(event)
            is StateEvent.QualityDegraded -> showQualityWarning(event)
            is StateEvent.RecoverySuccess -> showRecoverySuccess(event)
            else -> {}
        }
    }
}

// Monitor synchronization events
lifecycleScope.launch {
    enhancedScannerManager.synchronizationEvents.collect { event ->
        when (event) {
            is SynchronizationEvent.ConflictResolution -> handleConflict(event)
            is SynchronizationEvent.QualityBasedSwitching -> handleSwitch(event)
            else -> {}
        }
    }
}

// Force state synchronization
enhancedScannerManager.synchronizeAllStates()

// Trigger manual recovery
enhancedScannerManager.triggerConnectionRecovery(connectionId)

// Get connection state info
val stateInfo = enhancedScannerManager.getConnectionStateInfo(connectionId)
println("Quality Score: ${stateInfo?.connectionQuality?.qualityScore}")

// Clear persisted state (for testing)
enhancedScannerManager.clearAllPersistedState()
```

## State Models

### ConnectionStateInfo
Contains complete information about a connection's state:
- Connection ID and scanner information
- Current connection state
- State transition history
- Connection quality metrics
- Timestamps and durations

### ConnectionQuality
Tracks connection quality metrics:
- Signal strength (for wireless connections)
- Response time
- Error rate
- Throughput
- Quality score (0-100)
- Health status

### GlobalConnectionState
Overall system state:
- Total/active/connecting/error connection counts
- Connections grouped by type
- Overall health assessment
- Last update timestamp

## State Events

### StateEvent
Events emitted by the state manager:
- `ConnectionRegistered`: New connection registered
- `ConnectionUnregistered`: Connection removed
- `StateChanged`: Connection state changed
- `ConnectionEstablished`: Connection successfully established
- `ConnectionError`: Connection error occurred
- `ConnectionLost`: Connection lost
- `QualityDegraded`: Connection quality degraded
- `RecoveryAttempt`: Recovery initiated
- `RecoverySuccess`: Recovery succeeded
- `RecoveryFailed`: Recovery failed
- `GlobalStateChanged`: Global state updated
- `StateRecovered`: State recovered from persistence

### SynchronizationEvent
Events emitted by the synchronizer:
- `ConnectionPriorityResolution`: Priority-based conflict resolution
- `ConflictResolution`: Connection conflict resolved
- `ResourceConstraintResolution`: Resource constraint handled
- `QualityBasedSwitching`: Switched to better quality connection
- `InconsistencyDetected`: State inconsistency found
- `ResourceWarning`: Approaching resource limits
- `BackupActivation`: Backup connection activated

## Configuration

### Connection Priorities
Default priority order (highest to lowest):
1. J2534 (Professional diagnostic tools)
2. USB (Wired connections)
3. WiFi (Network connections)
4. Bluetooth Classic (Wireless)
5. Bluetooth LE (Low energy wireless)

### Resource Limits
- Maximum concurrent connections: 5
- Maximum reconnection attempts: 3
- Reconnection delay: 1-10 seconds (exponential backoff)
- Quality degradation threshold: Score < 40

### Persistence
- State directory: `{app_files}/scanner_state/`
- Connection states: `{state_dir}/connections/{connection_id}.json`
- Global state: `{state_dir}/global_state.json`

## Testing

The state management system is designed to be testable:
- All components accept injected dependencies
- State flows can be collected in tests
- Repository can be mocked for unit tests
- State can be cleared for test isolation

## Requirements Validation

This implementation satisfies the following requirements:

**Requirement 6.3**: Connection statistics show degraded performance → System suggests connection optimization
- Implemented via ConnectionQuality monitoring and QualityDegraded events

**Requirement 7.2**: Connection performance degrades → System alerts user and suggests corrective actions
- Implemented via quality monitoring, alerts, and automatic quality-based switching

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│              EnhancedScannerManager                         │
│         (Facade with state management)                      │
└─────────────────────────────────────────────────────────────┘
                              │
        ┌─────────────────────┼─────────────────────┐
        │                     │                     │
┌───────▼────────┐  ┌────────▼────────┐  ┌────────▼────────┐
│ Base Scanner   │  │ Connection      │  │ State           │
│ Manager        │  │ State Manager   │  │ Synchronizer    │
└────────────────┘  └─────────────────┘  └─────────────────┘
                              │
                    ┌─────────▼─────────┐
                    │ Connection State  │
                    │ Repository        │
                    └───────────────────┘
                              │
                    ┌─────────▼─────────┐
                    │ Local Storage     │
                    │ (JSON Files)      │
                    └───────────────────┘
```

## Future Enhancements

Potential improvements for future iterations:
- Remote state synchronization across devices
- Machine learning for connection quality prediction
- Advanced recovery strategies based on error patterns
- State analytics and reporting
- Connection pooling optimization
- Multi-device coordination
package com.spacetec.features.keyprogramming.key

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spacetec.core.common.result.Result
import com.spacetec.protocol.safety.SafetyManager
import com.spacetec.protocol.safety.SafetyCriticalOperation
import com.spacetec.protocol.safety.VehicleStatus
import com.spacetec.protocol.security.SecurityManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class KeyListViewModel @Inject constructor(
    private val safetyManager: SafetyManager,
    private val securityManager: SecurityManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(KeyListUiState())
    val uiState: StateFlow<KeyListUiState> = _uiState.asStateFlow()

    private val _securityToken = MutableStateFlow<String?>(null)

    init {
        loadKeys()
    }

    fun loadKeys() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            try {
                // Validate security for this operation
                val token = _securityToken.value
                val validation = securityManager.validateOperation(
                    SecurityManager.SecurityPermission.READ_LIVE_DATA, // Reading key info is similar to live data
                    "default_session",
                    token
                )

                if (!validation.isValid) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Security validation failed: ${validation.reason}"
                    )
                    return@launch
                }

                // Simulate loading keys
                delay(500)

                val keys = listOf(
                    KeyInfo("Key 1", "ID: 001", true),
                    KeyInfo("Key 2", "ID: 002", false),
                    KeyInfo("Key 3", "ID: 003", true),
                    KeyInfo("Spare Key", "ID: 004", false)
                )

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    keys = keys
                )

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to load keys: ${e.message}"
                )
            }
        }
    }

    fun addNewKey() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            try {
                // Perform safety checks for adding a new key
                val vehicleStatus = getVehicleStatus()
                val safetyCheck = safetyManager.performPreOperationChecks(
                    SafetyCriticalOperation.ECU_CODING, // Adding keys is similar to coding
                    vehicleStatus
                )

                if (!safetyCheck.isSafe) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Safety check failed: ${safetyCheck.issues.joinToString(", ") { it.message }}"
                    )
                    return@launch
                }

                // Validate security for this operation
                val token = _securityToken.value
                val validation = securityManager.validateOperation(
                    SecurityManager.SecurityPermission.ECU_PROGRAMMING,
                    "default_session",
                    token
                )

                if (!validation.isValid) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Security validation failed: ${validation.reason}"
                    )
                    return@launch
                }

                // Simulate adding new key
                delay(1000)

                val newKey = KeyInfo("New Key", "ID: ${System.currentTimeMillis()}", false)
                val updatedKeys = _uiState.value.keys + newKey

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    keys = updatedKeys
                )

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to add key: ${e.message}"
                )
            }
        }
    }

    fun deleteKey(keyId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            try {
                // Validate security for this operation
                val token = _securityToken.value
                val validation = securityManager.validateOperation(
                    SecurityManager.SecurityPermission.ECU_PROGRAMMING,
                    "default_session",
                    token
                )

                if (!validation.isValid) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Security validation failed: ${validation.reason}"
                    )
                    return@launch
                }

                val updatedKeys = _uiState.value.keys.filter { it.id != keyId }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    keys = updatedKeys
                )

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to delete key: ${e.message}"
                )
            }
        }
    }

    fun setSecurityToken(token: String) {
        _securityToken.value = token
    }

    private fun getVehicleStatus(): VehicleStatus {
        // In a real implementation, this would interface with actual vehicle status monitoring
        return VehicleStatus(
            engineRunning = false,
            voltage = 12.6,
            vehicleSpeed = 0.0,
            rpm = 0,
            transmissionPosition = "P",
            brakeApplied = true,
            temperature = 20.0
        )
    }

    fun resetState() {
        _uiState.value = KeyListUiState()
    }

    private suspend fun delay(timeMillis: Long) {
        kotlinx.coroutines.delay(timeMillis)
    }
}

data class KeyListUiState(
    val isLoading: Boolean = false,
    val keys: List<KeyInfo> = emptyList(),
    val errorMessage: String? = null
)

data class KeyInfo(
    val name: String,
    val id: String,
    val isActive: Boolean
)
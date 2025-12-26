package com.spacetec.features.dtc.presentation.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spacetec.features.dtc.data.FakeDtcData
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DTCScanViewModel : ViewModel() {
    
    private val _uiState = MutableStateFlow<DTCScanUiState>(DTCScanUiState.Idle)
    val uiState: StateFlow<DTCScanUiState> = _uiState.asStateFlow()
    
    fun startScan() {
        viewModelScope.launch {
            try {
                _uiState.value = DTCScanUiState.Scanning("Connecting to vehicle...")
                delay(1000)
                
                _uiState.value = DTCScanUiState.Scanning("Reading stored DTCs...")
                delay(1500)
                
                _uiState.value = DTCScanUiState.Scanning("Reading pending DTCs...")
                delay(1000)
                
                _uiState.value = DTCScanUiState.Scanning("Processing results...")
                delay(500)
                
                // Simulate finding DTCs
                val foundDTCs = simulateDTCsFound()
                _uiState.value = DTCScanUiState.Success(foundDTCs)
                
            } catch (e: Exception) {
                _uiState.value = DTCScanUiState.Error(e.message ?: "Scan failed")
            }
        }
    }
    
    fun stopScan() {
        _uiState.value = DTCScanUiState.Idle
    }
    
    private suspend fun simulateDTCsFound(): List<ScannedDTC> {
        // Simple stub data until scanner/database integration is enabled.
        return FakeDtcData.scanned
    }
}

sealed class DTCScanUiState {
    object Idle : DTCScanUiState()
    data class Scanning(val progress: String) : DTCScanUiState()
    data class Success(val dtcs: List<ScannedDTC>) : DTCScanUiState()
    data class Error(val message: String) : DTCScanUiState()
}

data class ScannedDTC(
    val code: String,
    val description: String,
    val status: String
)

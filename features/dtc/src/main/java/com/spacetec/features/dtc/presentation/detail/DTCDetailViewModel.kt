package com.spacetec.features.dtc.presentation.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spacetec.features.dtc.data.FakeDtcData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DTCDetailViewModel : ViewModel() {
    
    private val _uiState = MutableStateFlow<DTCDetailUiState>(DTCDetailUiState.Loading)
    val uiState: StateFlow<DTCDetailUiState> = _uiState.asStateFlow()
    
    fun loadDTC(code: String) {
        viewModelScope.launch {
            try {
                _uiState.value = DTCDetailUiState.Loading
                val dtc = FakeDtcData.listItems.firstOrNull { it.code == code }

                if (dtc != null) {
                    _uiState.value = DTCDetailUiState.Success(
                        DTCDetail(
                            code = dtc.code,
                            description = dtc.description,
                            explanation = "",
                            category = dtc.category,
                            system = dtc.system,
                            severity = dtc.severity,
                            possibleCauses = emptyList(),
                            symptoms = emptyList(),
                            diagnosticSteps = emptyList(),
                            isEmissionRelated = false
                        )
                    )
                } else {
                    _uiState.value = DTCDetailUiState.Error("DTC $code not found")
                }
            } catch (e: Exception) {
                _uiState.value = DTCDetailUiState.Error(e.message ?: "Failed to load DTC details")
            }
        }
    }
    
}

sealed class DTCDetailUiState {
    object Loading : DTCDetailUiState()
    data class Success(val dtc: DTCDetail) : DTCDetailUiState()
    data class Error(val message: String) : DTCDetailUiState()
}

data class DTCDetail(
    val code: String,
    val description: String,
    val explanation: String,
    val category: String,
    val system: String,
    val severity: String,
    val possibleCauses: List<String>,
    val symptoms: List<String>,
    val diagnosticSteps: List<String>,
    val isEmissionRelated: Boolean
)

package com.spacetec.features.dtc.presentation.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spacetec.features.dtc.data.FakeDtcData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DTCListViewModel : ViewModel() {
    
    private val _uiState = MutableStateFlow<DTCListUiState>(DTCListUiState.Loading)
    val uiState: StateFlow<DTCListUiState> = _uiState.asStateFlow()
    
    init {
        loadDTCs()
    }
    
    fun loadDTCs() {
        viewModelScope.launch {
            try {
                _uiState.value = DTCListUiState.Loading
                _uiState.value = DTCListUiState.Success(FakeDtcData.listItems)
            } catch (e: Exception) {
                _uiState.value = DTCListUiState.Error(e.message ?: "Unknown error")
            }
        }
    }
    
    fun searchDTCs(query: String) {
        viewModelScope.launch {
            try {
                _uiState.value = DTCListUiState.Loading
                val filtered = if (query.isBlank()) {
                    FakeDtcData.listItems
                } else {
                    FakeDtcData.listItems.filter {
                        it.code.contains(query, ignoreCase = true) ||
                            it.description.contains(query, ignoreCase = true)
                    }
                }
                _uiState.value = DTCListUiState.Success(filtered)
            } catch (e: Exception) {
                _uiState.value = DTCListUiState.Error(e.message ?: "Search failed")
            }
        }
    }
}

sealed class DTCListUiState {
    object Loading : DTCListUiState()
    data class Success(val dtcs: List<DTCListItem>) : DTCListUiState()
    data class Error(val message: String) : DTCListUiState()
}

data class DTCListItem(
    val code: String,
    val description: String,
    val severity: String,
    val category: String,
    val system: String
)

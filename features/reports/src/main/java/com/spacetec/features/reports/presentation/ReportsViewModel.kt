package com.spacetec.features.reports.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class ReportsViewModel @Inject constructor() : ViewModel() {
    
    private val _uiState = MutableStateFlow<ReportsUiState>(ReportsUiState.Loading)
    val uiState: StateFlow<ReportsUiState> = _uiState.asStateFlow()
    
    private val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    
    fun loadReports() {
        viewModelScope.launch {
            try {
                _uiState.value = ReportsUiState.Loading
                
                // Simulate loading reports
                val reports = generateSampleReports()
                _uiState.value = ReportsUiState.Success(reports)
                
            } catch (e: Exception) {
                _uiState.value = ReportsUiState.Error(e.message ?: "Failed to load reports")
            }
        }
    }
    
    fun generateNewReport() {
        viewModelScope.launch {
            try {
                val currentReports = (_uiState.value as? ReportsUiState.Success)?.reports ?: emptyList()
                val newReport = ReportItem(
                    id = UUID.randomUUID().toString(),
                    title = "Diagnostic Report #${currentReports.size + 1}",
                    date = dateFormat.format(Date()),
                    vehicleInfo = "2020 Toyota Camry",
                    dtcCount = (0..5).random()
                )
                
                _uiState.value = ReportsUiState.Success(listOf(newReport) + currentReports)
                
            } catch (e: Exception) {
                _uiState.value = ReportsUiState.Error("Failed to generate report")
            }
        }
    }
    
    fun exportReport(reportId: String) {
        // TODO: Implement actual export functionality
    }
    
    fun exportAllReports() {
        // TODO: Implement export all functionality
    }
    
    fun deleteReport(reportId: String) {
        val currentState = _uiState.value
        if (currentState is ReportsUiState.Success) {
            val updatedReports = currentState.reports.filter { it.id != reportId }
            _uiState.value = ReportsUiState.Success(updatedReports)
        }
    }
    
    private fun generateSampleReports(): List<ReportItem> {
        return listOf(
            ReportItem(
                id = "1",
                title = "Diagnostic Report #1",
                date = "Dec 23, 2024 14:30",
                vehicleInfo = "2019 Honda Civic",
                dtcCount = 2
            ),
            ReportItem(
                id = "2",
                title = "Diagnostic Report #2",
                date = "Dec 22, 2024 09:15",
                vehicleInfo = "2021 Ford F-150",
                dtcCount = 0
            )
        )
    }
}

sealed class ReportsUiState {
    object Loading : ReportsUiState()
    data class Success(val reports: List<ReportItem>) : ReportsUiState()
    data class Error(val message: String) : ReportsUiState()
}

data class ReportItem(
    val id: String,
    val title: String,
    val date: String,
    val vehicleInfo: String,
    val dtcCount: Int
)

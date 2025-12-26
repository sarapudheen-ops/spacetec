package com.spacetec.features.livedata.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import kotlin.random.Random

@HiltViewModel
class LiveDataViewModel @Inject constructor() : ViewModel() {
    
    private val _uiState = MutableStateFlow(LiveDataUiState())
    val uiState: StateFlow<LiveDataUiState> = _uiState.asStateFlow()
    
    private var monitoringJob: Job? = null
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    
    fun startMonitoring() {
        if (monitoringJob?.isActive == true) return
        
        _uiState.value = _uiState.value.copy(
            isMonitoring = true,
            error = null
        )
        
        monitoringJob = viewModelScope.launch {
            try {
                while (true) {
                    updateLiveData()
                    delay(1000) // Update every second
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isMonitoring = false,
                    error = "Monitoring failed: ${e.message}"
                )
            }
        }
    }
    
    fun stopMonitoring() {
        monitoringJob?.cancel()
        _uiState.value = _uiState.value.copy(
            isMonitoring = false,
            error = null
        )
    }
    
    private fun updateLiveData() {
        val currentTime = dateFormat.format(Date())
        
        val liveData = listOf(
            LiveDataItem(
                name = "Engine RPM",
                description = "Engine rotations per minute",
                value = Random.nextDouble(800.0, 3000.0),
                unit = "rpm",
                lastUpdated = currentTime
            ),
            LiveDataItem(
                name = "Vehicle Speed",
                description = "Current vehicle speed",
                value = Random.nextDouble(0.0, 120.0),
                unit = "km/h",
                lastUpdated = currentTime
            ),
            LiveDataItem(
                name = "Engine Load",
                description = "Calculated engine load",
                value = Random.nextDouble(10.0, 85.0),
                unit = "%",
                lastUpdated = currentTime
            ),
            LiveDataItem(
                name = "Coolant Temperature",
                description = "Engine coolant temperature",
                value = Random.nextDouble(85.0, 105.0),
                unit = "°C",
                lastUpdated = currentTime
            ),
            LiveDataItem(
                name = "Throttle Position",
                description = "Throttle position sensor",
                value = Random.nextDouble(0.0, 100.0),
                unit = "%",
                lastUpdated = currentTime
            ),
            LiveDataItem(
                name = "Intake Air Temperature",
                description = "Intake air temperature",
                value = Random.nextDouble(20.0, 60.0),
                unit = "°C",
                lastUpdated = currentTime
            ),
            LiveDataItem(
                name = "MAF Air Flow",
                description = "Mass air flow rate",
                value = Random.nextDouble(2.0, 25.0),
                unit = "g/s",
                lastUpdated = currentTime
            ),
            LiveDataItem(
                name = "Fuel Pressure",
                description = "Fuel rail pressure",
                value = Random.nextDouble(250.0, 400.0),
                unit = "kPa",
                lastUpdated = currentTime
            )
        )
        
        _uiState.value = _uiState.value.copy(liveData = liveData)
    }
    
    override fun onCleared() {
        super.onCleared()
        stopMonitoring()
    }
}

data class LiveDataUiState(
    val isMonitoring: Boolean = false,
    val liveData: List<LiveDataItem> = emptyList(),
    val error: String? = null
)

data class LiveDataItem(
    val name: String,
    val description: String,
    val value: Double,
    val unit: String,
    val lastUpdated: String
) {
    val formattedValue: String
        get() = "${String.format("%.1f", value)} $unit"
}

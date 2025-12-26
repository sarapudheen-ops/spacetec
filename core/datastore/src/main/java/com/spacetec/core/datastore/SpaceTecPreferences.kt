package com.spacetec.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("spacetec_prefs")

class SpaceTecPreferences(context: Context) {
    private val dataStore = context.dataStore
    
    companion object {
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val UNITS_METRIC = booleanPreferencesKey("units_metric")
        val AUTO_CONNECT = booleanPreferencesKey("auto_connect")
        val LAST_DEVICE = stringPreferencesKey("last_device")
        val LAST_VIN = stringPreferencesKey("last_vin")
        val SCAN_TIMEOUT = intPreferencesKey("scan_timeout")
        val LIVE_DATA_INTERVAL = intPreferencesKey("live_data_interval")
    }
    
    val darkMode: Flow<Boolean> = dataStore.data.map { it[DARK_MODE] ?: false }
    val unitsMetric: Flow<Boolean> = dataStore.data.map { it[UNITS_METRIC] ?: true }
    val autoConnect: Flow<Boolean> = dataStore.data.map { it[AUTO_CONNECT] ?: true }
    val lastDevice: Flow<String?> = dataStore.data.map { it[LAST_DEVICE] }
    val lastVin: Flow<String?> = dataStore.data.map { it[LAST_VIN] }
    val scanTimeout: Flow<Int> = dataStore.data.map { it[SCAN_TIMEOUT] ?: 30 }
    val liveDataInterval: Flow<Int> = dataStore.data.map { it[LIVE_DATA_INTERVAL] ?: 500 }
    
    suspend fun setDarkMode(enabled: Boolean) = dataStore.edit { it[DARK_MODE] = enabled }
    suspend fun setUnitsMetric(metric: Boolean) = dataStore.edit { it[UNITS_METRIC] = metric }
    suspend fun setAutoConnect(enabled: Boolean) = dataStore.edit { it[AUTO_CONNECT] = enabled }
    suspend fun setLastDevice(device: String) = dataStore.edit { it[LAST_DEVICE] = device }
    suspend fun setLastVin(vin: String) = dataStore.edit { it[LAST_VIN] = vin }
    suspend fun setScanTimeout(seconds: Int) = dataStore.edit { it[SCAN_TIMEOUT] = seconds }
    suspend fun setLiveDataInterval(ms: Int) = dataStore.edit { it[LIVE_DATA_INTERVAL] = ms }
    suspend fun clear() = dataStore.edit { it.clear() }
}

package com.spacetec.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "spacetec_settings")

class SpaceTecDataStore(private val context: Context) {
    
    companion object {
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val AUTO_SCAN = booleanPreferencesKey("auto_scan")
        val UNITS = stringPreferencesKey("units")
        val LAST_CONNECTED_DEVICE = stringPreferencesKey("last_connected_device")
        val SCAN_TIMEOUT = intPreferencesKey("scan_timeout")
        val PROTOCOL_PREFERENCE = stringPreferencesKey("protocol_preference")
    }
    
    val darkMode: Flow<Boolean> = context.dataStore.data.map { it[DARK_MODE] ?: false }
    val notificationsEnabled: Flow<Boolean> = context.dataStore.data.map { it[NOTIFICATIONS_ENABLED] ?: true }
    val autoScan: Flow<Boolean> = context.dataStore.data.map { it[AUTO_SCAN] ?: false }
    val units: Flow<String> = context.dataStore.data.map { it[UNITS] ?: "Metric" }
    val lastConnectedDevice: Flow<String?> = context.dataStore.data.map { it[LAST_CONNECTED_DEVICE] }
    val scanTimeout: Flow<Int> = context.dataStore.data.map { it[SCAN_TIMEOUT] ?: 30 }
    
    suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { it[DARK_MODE] = enabled }
    }
    
    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[NOTIFICATIONS_ENABLED] = enabled }
    }
    
    suspend fun setAutoScan(enabled: Boolean) {
        context.dataStore.edit { it[AUTO_SCAN] = enabled }
    }
    
    suspend fun setUnits(units: String) {
        context.dataStore.edit { it[UNITS] = units }
    }
    
    suspend fun setLastConnectedDevice(address: String) {
        context.dataStore.edit { it[LAST_CONNECTED_DEVICE] = address }
    }
    
    suspend fun setScanTimeout(timeout: Int) {
        context.dataStore.edit { it[SCAN_TIMEOUT] = timeout }
    }
}

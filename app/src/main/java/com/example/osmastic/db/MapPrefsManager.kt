package com.example.osmastic.db

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.first

class MapPrefsManager(private val dataStore: DataStore<Preferences>) {
    // Keys
    private val coldStorageMapLat = doublePreferencesKey("map_latitude")
    private val coldStorageMapLon = doublePreferencesKey("map_longitude")
    private val coldStorageMapZoom = doublePreferencesKey("map_zoom")
//    private val coldStorageLastChannel = stringPreferencesKey("last_channel_id") // TODO make this one used when channels introduced

    // ➡️➡️➡️ INTERACTIVE SAVING VVV
    suspend fun saveMapPos(latitude: Double, longitude: Double, zoom: Double) {
        dataStore.edit { prefs ->
            prefs[coldStorageMapLat] = latitude
            prefs[coldStorageMapLon] = longitude
            prefs[coldStorageMapZoom] = zoom
        }
    }
//    suspend fun saveLastChan(channelId: String) { // TODO make this one used when channels introduced
//        dataStore.edit { prefs ->
//            prefs[coldStorageMapZoom] = channelId
//        }
//    }
    suspend fun getInitialMapPosition(): Triple<Double, Double, Double> {
        val prefs = dataStore.data.first()
        return Triple(
            prefs[coldStorageMapLat]  ?: 59.9343,
            prefs[coldStorageMapLon]  ?: 30.3351,
            prefs[coldStorageMapZoom] ?: 11.0
        )
    }
    // ➡️➡️➡️ INTERACTIVE SAVING VVV
}

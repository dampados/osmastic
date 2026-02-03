package com.example.osmastic.db

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.example.osmastic.StateMapModel
import kotlinx.coroutines.flow.first
import org.osmdroid.util.GeoPoint

class MapPrefsManager(private val dataStore: DataStore<Preferences>) {
    // Keys
    private val coldStorageMapLat = doublePreferencesKey("map_latitude")
    private val coldStorageMapLon = doublePreferencesKey("map_longitude")
    private val coldStorageMapZoom = doublePreferencesKey("map_zoom")
    private val coldStorageMapRotation = floatPreferencesKey("map_rotation")
//    private val coldStorageLastChannel = stringPreferencesKey("last_channel_id") // TODO make this one used when channels introduced

    // ➡️➡️➡️ INTERACTIVE SAVING VVV
    suspend fun saveMapPos(incomingState: StateMapModel) {
        dataStore.edit { prefs ->
            prefs[coldStorageMapLat] = incomingState.mapCenter.latitude
            prefs[coldStorageMapLon] = incomingState.mapCenter.longitude
            prefs[coldStorageMapZoom] = incomingState.mapZoom
            prefs[coldStorageMapRotation] = incomingState.mapRotation
        }
    }
//    suspend fun saveLastChan(channelId: String) { // TODO make this one used when channels introduced
//        dataStore.edit { prefs ->
//            prefs[coldStorageMapZoom] = channelId
//        }
//    }
    suspend fun getInitialMapPosition(): StateMapModel {
        val prefs = dataStore.data.first()
        return(
                StateMapModel(
                    GeoPoint(
                       prefs[coldStorageMapLat]  ?: 59.9343,
                       prefs[coldStorageMapLon]  ?: 30.3351,
                   ),
                    prefs[coldStorageMapZoom] ?: 11.0,
                    prefs[coldStorageMapRotation] ?: 0f
                )
            )
    }
    // ➡️➡️➡️ INTERACTIVE SAVING VVV
}

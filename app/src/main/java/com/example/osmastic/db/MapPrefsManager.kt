package com.example.osmastic.db

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.dayanruben.maplibrecompose.core.CameraPosition
import com.dayanruben.spatialk.geojson.Position
import com.example.osmastic.StateMapModelU
import kotlinx.coroutines.flow.first

class MapPrefsManager(private val dataStore: DataStore<Preferences>) {
    // Keys
    private val coldStorageMapLat = doublePreferencesKey("map_latitude")
    private val coldStorageMapLon = doublePreferencesKey("map_longitude")
    private val coldStorageMapZoom = doublePreferencesKey("map_zoom")
    private val coldStorageMapBearing = doublePreferencesKey("map_bearing")
    private val coldStorageMapTilt = doublePreferencesKey("map_tilt")
//    private val coldStorageLastChannel = stringPreferencesKey("last_channel_id") // TODO make this one used when channels introduced

    // ➡️➡️➡️ INTERACTIVE SAVING VVV
    suspend fun saveMapPos(incomingState: StateMapModelU) {
        dataStore.edit { prefs ->
            prefs[coldStorageMapLat] = incomingState.cameraPosition.target.latitude
            prefs[coldStorageMapLon] = incomingState.cameraPosition.target.longitude
            prefs[coldStorageMapZoom] = incomingState.cameraPosition.zoom
            prefs[coldStorageMapBearing] = incomingState.cameraPosition.bearing
            prefs[coldStorageMapTilt] = incomingState.cameraPosition.tilt
        }
    }
//    suspend fun saveLastChan(channelId: String) { // TODO make this one used when channels introduced
//        dataStore.edit { prefs ->
//            prefs[coldStorageMapZoom] = channelId
//        }
//    }
    suspend fun getInitialMapPosition(): CameraPosition {
        val prefs = dataStore.data.first()
        return(
                CameraPosition(
                    target = Position(
                        longitude = prefs[coldStorageMapLon] ?: 30.3351,
                        latitude = prefs[coldStorageMapLat] ?: 59.9343
                    ),
                    zoom = prefs[coldStorageMapZoom] ?: 11.0,
                    bearing = prefs[coldStorageMapBearing] ?: 0.0,
                    tilt = prefs[coldStorageMapTilt] ?: 0.0
                )
            )
    }
    // ➡️➡️➡️ INTERACTIVE SAVING VVV
}

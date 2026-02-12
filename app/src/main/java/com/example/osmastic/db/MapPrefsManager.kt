package com.example.osmastic.db

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.example.osmastic.ViewPort
import kotlinx.coroutines.flow.first
import org.osmdroid.util.GeoPoint

class MapPrefsManager(private val dataStore: DataStore<Preferences>) {
    // Keys
    private val coldStorageMapLat = doublePreferencesKey("map_latitude")
    private val coldStorageMapLon = doublePreferencesKey("map_longitude")
    private val coldStorageMapZoom = doublePreferencesKey("map_zoom")
    private val coldStorageMapBearing = floatPreferencesKey("map_bearing")
//    private val coldStorageLastChannel = stringPreferencesKey("last_channel_id") // TODO make this one used when channels introduced

    // ➡️➡️➡️ INTERACTIVE SAVING VVV
    suspend fun saveMapPos(incomingViewPort: ViewPort) {
        dataStore.edit { prefs ->
            prefs[coldStorageMapLat] = incomingViewPort.mapCenter.latitude
            prefs[coldStorageMapLon] = incomingViewPort.mapCenter.longitude
            prefs[coldStorageMapZoom] = incomingViewPort.mapZoom
            prefs[coldStorageMapBearing] = incomingViewPort.mapBearing
        }
    }
//    suspend fun saveLastChan(channelId: String) { // TODO make this one used when channels introduced
//        dataStore.edit { prefs ->
//            prefs[coldStorageMapZoom] = channelId
//        }
//    }
    suspend fun getInitialMapPosition(): ViewPort {
        val prefs = dataStore.data.first()
        return(
                ViewPort(
                    mapCenter = GeoPoint(
                        prefs[coldStorageMapLat] ?: 59.9343,
                        prefs[coldStorageMapLon] ?: 30.3351,
                    ),
                    mapZoom = prefs[coldStorageMapZoom] ?: 11.0,
                    mapBearing = prefs[coldStorageMapBearing] ?: 0f,
                )
            )
    }
    // ➡️➡️➡️ INTERACTIVE SAVING VVV
}

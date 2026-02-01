package com.example.osmastic.db

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

class MapPrefsManager(private val dataStore: DataStore<Preferences>) {
    // Keys
    private val MAP_LAT = doublePreferencesKey("map_latitude")
    private val MAP_LON = doublePreferencesKey("map_longitude")
    private val MAP_ZOOM = doublePreferencesKey("map_zoom")
    private val LAST_CHANNEL = stringPreferencesKey("last_channel_id")

    // REACTIVE FLOWs (MAGIC)
    val mapLatitude: Flow<Double> = dataStore.data
        .map { prefs -> prefs[MAP_LAT] ?: 59.9343 } // SPB defaults
    val mapLongitude: Flow<Double> = dataStore.data
        .map { prefs -> prefs[MAP_LON] ?: 30.3351 } // SPB defaults
    val mapZoom: Flow<Double> = dataStore.data
        .map { prefs -> prefs[MAP_ZOOM] ?: 11.0 }
    val lastChannelId: Flow<String?> = dataStore.data // nullable
        .map { prefs -> prefs[LAST_CHANNEL] }

    // ➡️➡️➡️ INTERACTIVE SAVING VVV
    suspend fun saveMapPos(latitude: Double, longitude: Double, zoom: Double) {
        dataStore.edit { prefs ->
            prefs[MAP_LAT] = latitude
            prefs[MAP_LON] = longitude
            prefs[MAP_ZOOM] = zoom
        }
    }
    suspend fun saveLastChan(channelId: String) {
        dataStore.edit { prefs ->
            prefs[LAST_CHANNEL] = channelId
        }
    }
    suspend fun getInitialMapPosition(): Triple<Double, Double, Double> {
        val prefs = dataStore.data.first()
        return Triple(
            prefs[MAP_LAT]  ?: 59.9343,
            prefs[MAP_LON]  ?: 30.3351,
            prefs[MAP_ZOOM] ?: 11.0
        )
    }
    // SYNC ONE - only for initial reading. avoid racing
//    fun getInitialMapPosition(): Triple<Double, Double, Double> {
//        val prefs: Preferences = runBlocking<Preferences> { // OMG RUNTIMES NIGHTMARES DONT TOUCH ANYMORE
//            dataStore.data.first()
//        }
//        val latitude  = prefs[MAP_LAT]  ?: 59.9343
//        val longitude = prefs[MAP_LON]  ?: 30.3351
//        val zoom      = prefs[MAP_ZOOM] ?: 11.0
//
//        return Triple(latitude, longitude, zoom)
//    }
    // ➡️➡️➡️ INTERACTIVE SAVING VVV
}

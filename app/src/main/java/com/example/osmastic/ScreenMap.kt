@file:Suppress("COMPOSE_APPLIER_CALL_MISMATCH")

package com.example.osmastic

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
//new ones:
import android.view.MotionEvent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
// import our db/mapprefsmanager
import com.example.osmastic.db.MapPrefsManager
import kotlinx.coroutines.launch
// DATASTORE needs these to parse:
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay



// 📥📥📥 SCREEN WIDE STATE 📥📥📥
data class StateMapModel(
    val mapCenter: GeoPoint = GeoPoint(59.9343, 30.3351), // default loc, SPB
    val mapZoom: Double = 11.0 // obvious
)

class StateMapViewModel(application: Application) : AndroidViewModel(application) {
    val mapPrefsManager: MapPrefsManager by lazy {
        MapPrefsManager(
            PreferenceDataStoreFactory.create(
                produceFile = { application.filesDir.resolve("map_prefs.preferences_pb") }
            )
        )
    }
    private val _uiState = MutableStateFlow(StateMapModel()) // RW, but private!
    val uiState: StateFlow<StateMapModel> = _uiState.asStateFlow() // readonly!

    // ➡️➡️➡️ INTERACTIVE
    suspend fun saveCurrentPosition() {
        val state = _uiState.value
        mapPrefsManager.saveMapPos(
            latitude = state.mapCenter.latitude,
            longitude = state.mapCenter.longitude,
            zoom = state.mapZoom
        )
    }
    private var saveJob: Job? = null // DECLARE TO CANCEL
    fun updateMapPosition(center: GeoPoint, zoom: Double) {
        _uiState.value = _uiState.value.copy(
            mapCenter = center,
            mapZoom   = zoom
        )
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(500)
            saveCurrentPosition()
        }
    }
    // ➡️➡️➡️ INTERACTIVE
}
// 📥📥📥 SCREEN WIDE STATE 📥📥📥

@Composable
fun ScreenMap(viewModel: StateMapViewModel, modifier: Modifier = Modifier) {

//    val viewModel: StateMapViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    val hasAddedFirstLayoutListener = remember { mutableStateOf(false) } // HACK TO SKIP A STEP IN update()

    AndroidView(
        factory = { ctx ->
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK )
                setMultiTouchControls(true)

                // UPDATING RAM STATE EACH TIME USER RELEASES FINGER - SMART
                setOnTouchListener { fuckingViewValNotNeededForMaps, event -> // TODO rename when calmed down
                    if (event.action == MotionEvent.ACTION_UP) {
                        viewModel.updateMapPosition(
                            center = GeoPoint(
                                projection.currentCenter.latitude,
                                projection.currentCenter.longitude
                            ),
                            zoom = zoomLevelDouble
                        )
                        fuckingViewValNotNeededForMaps.performClick()
                        true
                    } else false // ???
                }
            }
        },
        update = { mapView ->

            //COLD STORAGE -> STATE (hack)
            if (!hasAddedFirstLayoutListener.value) {
                mapView.addOnFirstLayoutListener { _, _, _, _, _ ->
                    viewModel.viewModelScope.launch {
                        val (lat, lon, zoom) = viewModel.mapPrefsManager.getInitialMapPosition()
                        viewModel.updateMapPosition(GeoPoint(lat, lon), zoom)
                    }
                }
                hasAddedFirstLayoutListener.value = true
            }

            // STATE -> VIEW
            mapView.controller.setCenter(uiState.mapCenter)
            mapView.controller.setZoom(uiState.mapZoom)
        }
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.statusBarsPadding()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Zoom: ${uiState.mapZoom}", fontSize = 14.sp)
            Text("${uiState.mapCenter}", fontSize = 14.sp)
        }
    }
}
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
import org.osmdroid.views.CustomZoomButtonsController
// manual IMPORT of MapListener - thats our emit cathcher to react! but we react debouncing . . .
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
// manual import for rotation support
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay
import kotlin.math.abs

// 📥📥📥 SCREEN WIDE STATE 📥📥📥
data class StateMapModel(
    val mapCenter: GeoPoint = GeoPoint(59.9343, 30.3351), // default loc, SPB
    val mapZoom: Double = 11.0, // obvious
    val mapRotation: Float = 0f
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
        mapPrefsManager.saveMapPos(state)
    }

    private var jobStateUpdate: Job? = null
    private var jobStateColdUpdate: Job? = null
    fun updateMapPosition(incomingState: StateMapModel) {
        //#1 - RAM FLUSH - QUICKER!
        jobStateUpdate?.cancel()
        jobStateUpdate = viewModelScope.launch {
            delay(200L)
            val currentState = _uiState.value
//            if (isEqual(currentState, incomingState)) return@launch // break lambda call if TRUE
            _uiState.value = incomingState.copy()
        }

        //#2 COLD FLUSH - RELAXED!
        jobStateColdUpdate?.cancel()
        jobStateColdUpdate = viewModelScope.launch {
            delay(1500L)
            saveCurrentPosition()
        }
    }

    private fun isEqual(a: StateMapModel, b: StateMapModel): Boolean {
        val centerEpsilon = 1e-5
        val zoomEpsilon   = 1e-4
        return abs(a.mapCenter.latitude  - b.mapCenter.latitude)  < centerEpsilon &&
                abs(a.mapCenter.longitude - b.mapCenter.longitude) < centerEpsilon &&
                abs(a.mapZoom - b.mapZoom) < zoomEpsilon
    }
    // ➡️➡️➡️ INTERACTIVE
}
// 📥📥📥 SCREEN WIDE STATE 📥📥📥

@Composable
fun ScreenMap(viewModel: StateMapViewModel, modifier: Modifier = Modifier) {
    val uiState by viewModel.uiState.collectAsState()
    val hasAddedFirstLayoutListener = remember { mutableStateOf(false) } // HACK TO SKIP A STEP IN update()

    AndroidView<MapView>(
        factory = { ctx ->
            MapView(ctx).apply {

                // CONFIG 🚧🚧🚧
                setTileSource(TileSourceFactory.MAPNIK )
                setMultiTouchControls(true)
                setUseDataConnection(true)
                zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)

                val rotationOverlay = RotationGestureOverlay(this)
                rotationOverlay.isEnabled = true
                overlays.add(rotationOverlay)

                // VIEW -> MODEL (bottom -> top) UPDATE
                addMapListener(object : MapListener {
                    override fun onScroll(event: ScrollEvent?): Boolean {
                        val newState = StateMapModel(
                            mapCenter = GeoPoint(this@apply.mapCenter.latitude, this@apply.mapCenter.longitude),
                            mapZoom = this@apply.zoomLevelDouble,
                            mapRotation = this@apply.mapOrientation,
                        )
                        viewModel.updateMapPosition(newState)
                        return false
                    }
                    override fun onZoom(event: ZoomEvent?): Boolean {
                        val newState = StateMapModel(
                            mapCenter = GeoPoint(this@apply.mapCenter.latitude, this@apply.mapCenter.longitude),
                            mapZoom = this@apply.zoomLevelDouble,
                            mapRotation = this@apply.mapOrientation,
                        )
                        viewModel.updateMapPosition(newState)
                        return false
                    }


                })
                // CONFIG 🚧🚧🚧
            }
        },
        update = { mapView ->

            //COLD STORAGE -> STATE (hack)
            if (!hasAddedFirstLayoutListener.value) {
                mapView.addOnFirstLayoutListener { _, _, _, _, _ ->
                    viewModel.viewModelScope.launch {
                        val coldState = viewModel.mapPrefsManager.getInitialMapPosition()
                        viewModel.updateMapPosition(coldState) // HAHAHA just pass the state, dear
                    }
                }
                hasAddedFirstLayoutListener.value = true
            }

            // STATE -> VIEW (smart, checks if interactive before recomposition)
                mapView.controller.setCenter(uiState.mapCenter)
                mapView.controller.setZoom(uiState.mapZoom)
                mapView.mapOrientation = uiState.mapRotation

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
            Text("${uiState.mapRotation}", fontSize = 14.sp)

        }
    }
}
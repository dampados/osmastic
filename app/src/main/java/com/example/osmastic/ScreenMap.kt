@file:Suppress("COMPOSE_APPLIER_CALL_MISMATCH")

package com.example.osmastic

import android.app.Application
import android.content.Context
import android.widget.Toast
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import org.osmdroid.views.CustomZoomButtonsController
// manual IMPORT of MapListener - thats our emit cathcher to react! but we react debouncing . . .
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
// manual import for rotation support
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay

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
//            val currentState = _uiState.value
            _uiState.value = incomingState.copy()
        }

        //#2 COLD FLUSH - RELAXED!
        jobStateColdUpdate?.cancel()
        jobStateColdUpdate = viewModelScope.launch {
            delay(1000L)
            saveCurrentPosition()
        }
    }
    // ➡️➡️➡️ INTERACTIVE
}
// 📥📥📥 SCREEN WIDE STATE 📥📥📥

// ♻️🧭♻️🧭♻️🧭 MAP MANAGER!!! ♻️🧭♻️🧭♻️🧭
class OsmdroidManager(context: Context,                 // CLASS WRAPPER AROUND THE MapView !!!
                      private val onMapMovedCallback: (StateMapModel) -> Unit, // SIMPLE CALLBACK! TO HERE WE PLACE LATER WHAT WILL UPDATE BOTH HOT + COLD!
                      private val onMapReadyCallback: suspend () -> StateMapModel // SIMPLE CALLBACK! we put cold state loading call!!! on the event afer which its safe
    ) {
    private val mapView: MapView // OUTSOURCED MAPVIEW !!!

    init { // FACTORY ONE TIME INSTANTIATION
        mapView = MapView(context).apply {
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
//                    viewModel.updateMapPosition(newState)
                    onMapMovedCallback(newState)
                    return false
                }
                override fun onZoom(event: ZoomEvent?): Boolean {
                    val newState = StateMapModel(
                        mapCenter = GeoPoint(this@apply.mapCenter.latitude, this@apply.mapCenter.longitude),
                        mapZoom = this@apply.zoomLevelDouble,
                        mapRotation = this@apply.mapOrientation,
                    )
//                    viewModel.updateMapPosition(newState)
                    onMapMovedCallback(newState)
                    return false
                }
            })

            addOnFirstLayoutListener { _, _, _, _, _ ->
                CoroutineScope(Dispatchers.Main).launch {
                    val coldState = onMapReadyCallback() // ACTUALLY READ -> business logic viewmodel -> come back here
                    setViewport(coldState) // LOCAL ONLY
                }
            }
            // CONFIG 🚧🚧🚧
        }
    }

    fun getMapView(): MapView = mapView // Factory calls this

    fun setViewport(incomingState: StateMapModel) {
        mapView.mapOrientation = incomingState.mapRotation
        mapView.controller.setCenter(incomingState.mapCenter)
        mapView.controller.setZoom(incomingState.mapZoom)
    }

}
// ♻️🧭♻️🧭♻️🧭 MAP MANAGER!!! ♻️🧭♻️🧭♻️🧭

@Composable
fun ScreenMap(viewModel: StateMapViewModel, modifier: Modifier = Modifier) {
    val uiState by viewModel.uiState.collectAsState()

    AndroidView<MapView>(
        factory = { ctx ->
            OsmdroidManager(ctx,
                onMapMovedCallback = { viewModel.updateMapPosition(it) },
                onMapReadyCallback = {
                    val coldState = viewModel.mapPrefsManager.getInitialMapPosition()
                    viewModel.updateMapPosition(coldState)
                    coldState // we return it back! CALLBACK HELL HAHAHAHH
                },
                ).getMapView()
        },
        update = { /* nothing here - not using native MVVM updates */  }
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
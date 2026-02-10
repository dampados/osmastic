package com.example.osmastic

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.spatialk.geojson.Position
import com.example.osmastic.db.MapPrefsManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.maplibre.android.annotations.Marker
import org.maplibre.android.geometry.LatLng
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.style.BaseStyle

// 📥📥📥 SCREEN WIDE STATE 📥📥📥
data class StateMapModelU(
    val cameraPosition: CameraPosition = CameraPosition(
        target = Position(30.3351, 59.9343),
        zoom = 11.0,
        bearing = 0.0, // rotation
        tilt = 0.0,
    )

    //TODO add list of pins HOT PINS
)
class StateMapViewModelU(application: Application) : AndroidViewModel(application) {
    val mapPrefsManager: MapPrefsManager by lazy {
        MapPrefsManager(
            PreferenceDataStoreFactory.create(
                produceFile = { application.filesDir.resolve("map_prefs.preferences_pb") }
            )
        )
    }
    private val _mapStateRW = MutableStateFlow(StateMapModelU()) // RW, but private!
    val mapStateR: StateFlow<StateMapModelU> = _mapStateRW.asStateFlow() // readonly!

    // COLD BOOT !!! load cold state
    init {
        viewModelScope.launch {
            val coldViewPortPrefs = mapPrefsManager.getInitialMapPosition()
//            _mapStateRW.value = StateMapModelU(cameraPosition = loadedPosition)
            _mapStateRW.update { current ->
                current.copy(cameraPosition = coldViewPortPrefs)
            }
        }
    }

    // 🤫🤫🤫 INTERACTIVE PRIVATE 🤫🤫🤫
    suspend fun saveCurrentPosition() {
        val state = _mapStateRW.value
        mapPrefsManager.saveMapPos(state)
    }
    // 🤫🤫🤫 INTERACTIVE PRIVATE 🤫🤫🤫

    // ➡️➡️➡️ INTERACTIVE ➡️➡️➡️
    private var jobStateUpdate: Job? = null
    private var jobStateColdUpdate: Job? = null
    fun updateViewPortState(incomingViewPort: CameraPosition) {
        //#1 - RAM FLUSH - QUICKER!
        jobStateUpdate?.cancel()
        jobStateUpdate = viewModelScope.launch {
            delay(200L)
//            _mapStateRW.value = incomingState.copy()
            _mapStateRW.update { current ->
                current.copy(cameraPosition = incomingViewPort)
            }
        }
        //#2 COLD FLUSH - RELAXED!
        jobStateColdUpdate?.cancel()
        jobStateColdUpdate = viewModelScope.launch {
            delay(1000L)
            saveCurrentPosition()
        }
    }
    // ➡️➡️➡️ INTERACTIVE ➡️➡️➡️
}
// 📥📥📥 SCREEN WIDE STATE 📥📥📥


@Composable
fun ScreenMapU(viewModel: StateMapViewModelU, modifier: Modifier = Modifier) {
    val stateOfModel by viewModel.mapStateR.collectAsState()
    val cameraState = rememberCameraState(stateOfModel.cameraPosition)

    //🎥🎥🎥 EFFECTS, OBSERVERS 🎥🎥🎥
    // TOP → BOTTOM: VM → map (one-way, reacts on state change)
    LaunchedEffect(stateOfModel.cameraPosition) {
    //   cameraState.position = viewModel.cameraPosition   // instant
         cameraState.animateTo(stateOfModel.cameraPosition) // smooth
    }
    // BOTTOM → TOP: map → VM (with debounce on move)
    LaunchedEffect(cameraState) {
        snapshotFlow { cameraState.position }
            .collect { incomingViewPort ->
//                val newState = StateMapModelU(cameraPosition = incomingViewPort)
                viewModel.updateViewPortState(incomingViewPort)
            }
    }
    //🎥🎥🎥 EFFECTS, OBSERVERS 🎥🎥🎥

    // <call composables HERE>

    MaplibreMap(
        modifier = modifier,
        // Free OSM tiles (requires internet and attribution)
        baseStyle = BaseStyle.Uri("https://tiles.openfreemap.org/styles/liberty"),
        cameraState = cameraState,
        // Optional: Disable gestures if you want to control via VM later
        // gestureSettings = GestureSettings(scrollEnabled = false)
    ) {

//        Marker(
//            position = LatLng(59.93, 30.33),
//            title = "Test Marker",
//            snippet = "Hello",
////            icon = painterResource(R.drawable.my_pin),
//            onClick = { true }
//        )

        // 1. Future Pin (Annotation) layers will go here
        // 2. Example: AnnotationLayer(annotationPlugin) { ... }
    }

}


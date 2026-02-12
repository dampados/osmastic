//package com.example.osmastic
//
//import android.app.Application
//import androidx.compose.foundation.layout.Box
//import androidx.compose.foundation.layout.Column
//import androidx.compose.foundation.layout.PaddingValues
//import androidx.compose.foundation.layout.statusBarsPadding
//import androidx.compose.material3.Text
//import androidx.compose.runtime.Composable
//import androidx.compose.runtime.LaunchedEffect
//import androidx.compose.runtime.collectAsState
//import androidx.compose.runtime.getValue
//import androidx.compose.runtime.snapshotFlow
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.unit.dp
//import androidx.compose.ui.unit.sp
//import androidx.datastore.preferences.core.PreferenceDataStoreFactory
//import androidx.lifecycle.AndroidViewModel
//import androidx.lifecycle.viewModelScope
//import com.dayanruben.maplibrecompose.compose.ClickResult
//import com.dayanruben.maplibrecompose.compose.MaplibreMap
//import com.dayanruben.maplibrecompose.compose.rememberCameraState
//import com.dayanruben.maplibrecompose.core.CameraPosition
//import com.dayanruben.maplibrecompose.core.OrnamentSettings
//import com.dayanruben.spatialk.geojson.Position
//import com.example.osmastic.db.MapPrefsManager
//import kotlinx.coroutines.Job
//import kotlinx.coroutines.delay
//import kotlinx.coroutines.flow.MutableStateFlow
//import kotlinx.coroutines.flow.StateFlow
//import kotlinx.coroutines.flow.asStateFlow
//import kotlinx.coroutines.flow.update
//import kotlinx.coroutines.launch
//import kotlin.random.Random
////IM TIRED
//data class PinHot(
//    val pinLogicalId: Int,
//    val lamportEpoch: Int = 1,
//    val editorHash: ByteArray,
//    val position: Position, // full maplibre postion for simplicity
//    val iconUnicode: Int = 0x1F4CD,
//    val label: String? = null,
//    val isHiddenBeforeTTL: Boolean = false, // 1 byte
//    val expirationTimestamp: Long = 0L,  // milliseconds full epoch (built from local epoch + 1 byte hours from message) 0 = no TTL
//)
//// 📥📥📥 SCREEN WIDE STATE 📥📥📥
//data class StateMapModelU(
//    val cameraPosition: CameraPosition = CameraPosition(
//        target = Position(30.3351, 59.9343),
//        zoom = 11.0,
//        bearing = 0.0, // rotation
//        tilt = 0.0,
//    ),
//    val pins: List<HotPin> = emptyList()
//)
//class StateMapViewModelU(application: Application) : AndroidViewModel(application) {
//    val mapPrefsManager: MapPrefsManager by lazy {
//        MapPrefsManager(
//            PreferenceDataStoreFactory.create(
//                produceFile = { application.filesDir.resolve("map_prefs.preferences_pb") }
//            )
//        )
//    }
//    private val _mapStateRWold = MutableStateFlow(StateMapModelU()) // RW, but private!
//    val mapStateRold: StateFlow<StateMapModelU> = _mapStateRWold.asStateFlow() // readonly!
//
//    // 🚀🚀🚀 COLD BOOT !!! load cold state 🚀🚀🚀
//    init {
//        viewModelScope.launch {
//            val coldViewPortPrefs = mapPrefsManager.getInitialMapPosition()
////            _mapStateRW.value = StateMapModelU(cameraPosition = loadedPosition)
//            _mapStateRWold.update { current ->
//                current.copy(cameraPosition = coldViewPortPrefs)
//            }
//        }
//    }
//    // 🚀🚀🚀 COLD BOOT !!! load cold state 🚀🚀🚀
//
//    // 🤫🤫🤫 INTERACTIVE PRIVATE 🤫🤫🤫
//    suspend fun saveCurrentPosition() {
//        val state = _mapStateRWold.value
//        mapPrefsManager.saveMapPos(state)
//    }
//    // 🤫🤫🤫 INTERACTIVE PRIVATE 🤫🤫🤫
//
//    // ➡️➡️➡️ INTERACTIVE ➡️➡️➡️
//    private var jobStateUpdate: Job? = null
//    private var jobStateColdUpdate: Job? = null
//    fun updateViewPortState(incomingViewPort: CameraPosition) {
//        //#1 - RAM FLUSH - QUICKER!
//        jobStateUpdate?.cancel()
//        jobStateUpdate = viewModelScope.launch {
//            delay(200L)
////            _mapStateRW.value = incomingState.copy()
//            _mapStateRWold.update { current ->
//                current.copy(cameraPosition = incomingViewPort)
//            }
//        }
//        //#2 COLD FLUSH - RELAXED!
//        jobStateColdUpdate?.cancel()
//        jobStateColdUpdate = viewModelScope.launch {
//            delay(1000L)
//            saveCurrentPosition()
//        }
//    }
//
//    fun pushQuickPin(incomingPosition: Position) {
//        val myEditorHash: ByteArray = ByteArray(3).apply { Random.nextBytes(this) }
//        val newPin = HotPin(
//            pinLogicalId = Random.nextInt(1_000_000, 2_000_000_000),
//            editorHash = myEditorHash,
//            position = incomingPosition,
//        )
//        _mapStateRWold.update { current ->
//            current.copy(pins = current.pins + newPin)
//        }
//    }
//    // ➡️➡️➡️ INTERACTIVE ➡️➡️➡️
//}
//// 📥📥📥 SCREEN WIDE STATE 📥📥📥
//
//
//@Composable
//fun ScreenMapU(viewModel: StateMapViewModelU, modifier: Modifier = Modifier) {
//    val stateOfModel by viewModel.mapStateRold.collectAsState() // the very DATA STATE, no methods IMPORTANT
//    val cameraState = rememberCameraState(stateOfModel.cameraPosition) // uuh... trust me bro, no way around it.
//
//    //🎥🎥🎥 EFFECTS, OBSERVERS 🎥🎥🎥
//    // TOP → BOTTOM: VM → map (one-way, reacts on state change)
//    LaunchedEffect(stateOfModel.cameraPosition) {
//    //   cameraState.position = viewModel.cameraPosition   // instant
//         cameraState.animateTo(stateOfModel.cameraPosition) // smooth
//    }
//    // BOTTOM → TOP: map → VM (with debounce on move)
//    LaunchedEffect(cameraState) {
//        snapshotFlow { cameraState.position }
//            .collect { incomingViewPort ->
//                viewModel.updateViewPortState(incomingViewPort)
//            }
//    }
//    //🎥🎥🎥 EFFECTS, OBSERVERS 🎥🎥🎥
//
//    MaplibreMap(
//        modifier = modifier,
//        styleUri = "https://tiles.openfreemap.org/styles/liberty",
//        cameraState = cameraState,
//        ornamentSettings = OrnamentSettings(
//            isLogoEnabled = false,
//            isCompassEnabled = true,
//            isScaleBarEnabled = true,
//            padding = PaddingValues(top = 25.dp)
//        ),
//        onMapClick = { position, _ ->
//            viewModel.pushQuickPin(position)
//            ClickResult.Consume
//        }
//    )  { // CONTENT OF THE MAP (SPECIAL FUNCS)
//
//        val pins = stateOfModel.pins   // ← this is all you need
//
//        // THIS IS CORRECT SYNTAX FOR 0.12.1
////        GeoJsonSource(
////            id = "pins-source",
////            data = FeatureCollection.fromFeatures(
////                pins.map { pin ->
////                    Feature.fromGeometry(
////                        Point.fromLngLat(
////                            pin.position.longitude,
////                            pin.position.latitude
////                        )
////                    ).apply {
////                        addStringProperty("label", pin.label ?: "")
////                        addNumberProperty("icon", pin.iconUnicode.toDouble())
////                    }
////                }
////            )
////        )
////
////        // THIS IS CORRECT SYNTAX FOR 0.12.1
////        SymbolLayer(
////            id = "pins-layer",
////            sourceId = "pins-source"
////        ) {
////            textField("{icon}\n{label}")
////            textSize(32f)
////            textLineHeight(1.25f)
////            textAllowOverlap(true)
////            textIgnorePlacement(true)
////            textAnchor(TextAnchor.TOP)
////        }
//
//    }
//
//    Box(
//        contentAlignment = Alignment.Center,
//        modifier = modifier.statusBarsPadding()
//    ) {
//        Column(
//            horizontalAlignment = Alignment.CenterHorizontally
//        ) {
//            Text("Zoom: ${stateOfModel.cameraPosition.zoom}", fontSize = 14.sp)
//            Text("${stateOfModel.cameraPosition.target}", fontSize = 14.sp)
//            Text("${stateOfModel.cameraPosition.bearing}", fontSize = 14.sp)
//            Text("${stateOfModel.pins}", fontSize = 6.sp)
////            Text("Center: ${viewModel.uiState.mapCenter.latitude}, ${viewModel.uiState.mapCenter.longitude}")
//
//        }
//    }
//
//
//
//}
//

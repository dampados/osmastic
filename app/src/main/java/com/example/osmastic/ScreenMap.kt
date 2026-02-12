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
import kotlinx.coroutines.flow.update
import org.osmdroid.views.CustomZoomButtonsController
// manual IMPORT of MapListener - thats our emit cathcher to react! but we react debouncing . . .
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
// manual import for rotation support
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay
// taps support
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.views.overlay.MapEventsOverlay

data class ViewPort(
    val mapCenter: GeoPoint, // default loc, SPB
    val mapZoom: Double, // obvious
    val mapBearing: Float
)
data class HotPin(
    val pinLogicalId: Int,
    val lamportEpoch: Int = 1,
    val editorHash: ByteArray,
    val viewPort: ViewPort,         // full viewport for simplicity
    val iconUnicode: Int = 0x1F4CD,
    val label: String? = null,
    val isHiddenBeforeTTL: Boolean = false, // 1 byte
    val expirationTimestamp: Long = 0L,  // milliseconds full epoch (built from local epoch + 1 byte hours from message) 0 = no TTL
)
// 📥📥📥 SCREEN WIDE STATE 📥📥📥
data class StateMapModel(
    val viewPort: ViewPort = ViewPort(
        mapCenter = GeoPoint(59.9343, 30.3351), // default loc, SPB
        mapZoom = 11.0,
        mapBearing = 0f
    ),
    val pins: List<HotPin> = emptyList()
)
class StateMapViewModel(application: Application) : AndroidViewModel(application) {
    val mapPrefsManager: MapPrefsManager by lazy {
        MapPrefsManager(
            PreferenceDataStoreFactory.create(
                produceFile = { application.filesDir.resolve("map_prefs.preferences_pb") }
            )
        )
    }
    private val _mapStateRW = MutableStateFlow(StateMapModel()) // RW, but private!
    val mapStateR: StateFlow<StateMapModel> = _mapStateRW.asStateFlow() // readonly!

    // ➡️➡️➡️ INTERACTIVE
    suspend fun saveViewPortToColdStorage() {
        val currentViewPort = _mapStateRW.value.viewPort
        mapPrefsManager.saveMapPos(currentViewPort)
    }

    private var jobStateUpdate: Job? = null
    private var jobStateColdUpdate: Job? = null
    fun updateViewPort(incomingViewPort: ViewPort) {
        //#1 - RAM FLUSH - QUICKER!
        jobStateUpdate?.cancel()
        jobStateUpdate = viewModelScope.launch {
            delay(200L)
            _mapStateRW.update { current ->
                current.copy(viewPort = incomingViewPort)
            }
        }
        //#2 COLD FLUSH - RELAXED!
        jobStateColdUpdate?.cancel()
        jobStateColdUpdate = viewModelScope.launch {
            delay(1000L)
            saveViewPortToColdStorage()
        }
    }
    // ➡️➡️➡️ INTERACTIVE
}
// 📥📥📥 SCREEN WIDE STATE 📥📥📥

// ♻️🧭♻️🧭♻️🧭 MAP MANAGER!!! ♻️🧭♻️🧭♻️🧭
class OsmdroidManager(context: Context,                 // CLASS WRAPPER AROUND THE MapView !!!
                      private val onMapMovedCallback: (ViewPort) -> Unit, // SIMPLE CALLBACK! TO HERE WE PLACE LATER WHAT WILL UPDATE BOTH HOT + COLD!
                      private val onMapReadyCallback: suspend () -> ViewPort, // SIMPLE CALLBACK! we put cold state loading call!!! on the event afer which its safe
                      private val onShortTapCallback: suspend (GeoPoint, Context) -> Unit,
                      private val onLongTapCallback: suspend (GeoPoint, Context) -> Unit,
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
                    val newViewPort = ViewPort(
                        mapCenter = GeoPoint(this@apply.mapCenter.latitude, this@apply.mapCenter.longitude),
                        mapZoom = this@apply.zoomLevelDouble,
                        mapBearing = this@apply.mapOrientation,
                    )
//                    viewModel.updateMapPosition(newState)
                    onMapMovedCallback(newViewPort)
                    return false
                }
                override fun onZoom(event: ZoomEvent?): Boolean {
                    val newViewPort = ViewPort(
                        mapCenter = GeoPoint(this@apply.mapCenter.latitude, this@apply.mapCenter.longitude),
                        mapZoom = this@apply.zoomLevelDouble,
                        mapBearing = this@apply.mapOrientation,
                    )
//                    viewModel.updateMapPosition(newState)
                    onMapMovedCallback(newViewPort)
                    return false
                }
            })

            overlays.add(MapEventsOverlay(object : MapEventsReceiver {
                override fun singleTapConfirmedHelper(geoPoint: GeoPoint?): Boolean {
                    CoroutineScope(Dispatchers.Main).launch {
                        geoPoint?.let { geoPoint ->                                                       // "if not null, do something".
                            onShortTapCallback(geoPoint, context)
                        }
                    }
                    return true
                }
                override fun longPressHelper(geoPoint: GeoPoint?): Boolean {
                    CoroutineScope(Dispatchers.Main).launch {
                        geoPoint?.let { geoPoint ->                                                  // "if not null, do something".
                            onLongTapCallback(geoPoint, context)
                        }
                    }
                    return true
                }
            }))

            // ON MAP READY CATCH
            addOnFirstLayoutListener { _, _, _, _, _ ->
                CoroutineScope(Dispatchers.Main).launch {
                    val coldViewPort = onMapReadyCallback() // ACTUALLY READ -> business logic viewmodel -> come back here
                    setViewport(coldViewPort) // LOCAL ONLY
                }
            }
            // CONFIG 🚧🚧🚧
        }
    }

    fun getMapView(): MapView = mapView // Factory calls this

    fun setViewport(incomingViewPort: ViewPort) {
        mapView.controller.animateTo(
            incomingViewPort.mapCenter,
            incomingViewPort.mapZoom,
            5,
            incomingViewPort.mapBearing,
//            0  // 0ms = instant
        )
    }
}
// ♻️🧭♻️🧭♻️🧭 MAP MANAGER!!! ♻️🧭♻️🧭♻️🧭

@Composable
fun ScreenMap(viewModel: StateMapViewModel, modifier: Modifier = Modifier) {
    val stateOfModel by viewModel.mapStateR.collectAsState()

    AndroidView<MapView>(
        factory = { ctx ->
            OsmdroidManager(ctx,
                onMapMovedCallback = { viewModel.updateViewPort(it) },
                onMapReadyCallback = {
                    val coldViewPort = viewModel.mapPrefsManager.getInitialMapPosition()
                    viewModel.updateViewPort(coldViewPort)
                    coldViewPort        // we return it back! CALLBACK "HELL" HAHAHAHH
                },
                onShortTapCallback = { geoPoint, context ->
                    Toast.makeText(context, "SHORT: ${geoPoint.latitude}, ${geoPoint.longitude}", Toast.LENGTH_SHORT).show()
                },
                onLongTapCallback = { geoPoint, context ->
                    Toast.makeText(context, "LONG: ${geoPoint.latitude}, ${geoPoint.longitude}", Toast.LENGTH_LONG).show()
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
            Text("Zoom: ${stateOfModel.viewPort.mapZoom}", fontSize = 14.sp)
            Text("${stateOfModel.viewPort.mapCenter}", fontSize = 14.sp)
            Text("${stateOfModel.viewPort.mapBearing}", fontSize = 14.sp)
//            Text("Center: ${viewModel.uiState.mapCenter.latitude}, ${viewModel.uiState.mapCenter.longitude}")

        }
    }
}
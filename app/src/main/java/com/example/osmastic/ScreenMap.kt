@file:Suppress("COMPOSE_APPLIER_CALL_MISMATCH")

package com.example.osmastic

import android.app.Application
import android.content.Context
import android.graphics.Canvas
import android.text.TextPaint
import android.view.Surface
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
//new ones:
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
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
import org.osmdroid.views.overlay.Marker
import java.security.SecureRandom

class LabeledMarker(
    mapView: MapView,
    private val label: String,
    private val rotation: Float? = null  // 👈 ADD THIS
) : Marker(mapView) {

    init {
        // 👇 NULL = upright (flat=false), VALUE = rotates with map (flat=true)
        isFlat = rotation != null
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        rotation?.let { setRotation(it) }
    }
    private val textPaint = TextPaint().apply {
        color = android.graphics.Color.BLACK
        textSize = 48f
        textAlign = android.graphics.Paint.Align.CENTER
    }
    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        super.draw(canvas, mapView, shadow)

        if (!shadow) {
            val point = mapView.projection.toPixels(position, null)
            val gapPx = 20 * mapView.resources.displayMetrics.density
            // 👇 LABEL ALWAYS FLAT (counter-rotate)
            canvas.save()
            canvas.rotate(-mapView.mapOrientation, point.x.toFloat(), point.y + gapPx)
            canvas.drawText(label, point.x.toFloat(), point.y + gapPx, textPaint)
            canvas.restore()
        }
    }
}
data class ViewPort(
    val mapCenter: GeoPoint, // default loc, SPB
    val mapZoom: Double, // obvious
    val mapBearing: Float
)

data class PinUI(
    val geoPoint: GeoPoint,
    val iconUnicode: String = "📍",
    val label: String? = null,
    val rotationByte: Int? = null,
    val isHiddenBeforeTTL: Boolean = false, // 1 byte
)
data class PinLogical(
    val pinLogicalId: Int,
    val lamportEpoch: Int = 1,
    val editorHash: ByteArray,
    val expirationTimestamp: Long = 0L,  // milliseconds full epoch (built from local epoch + 1 byte hours from message) 0 = no TTL
    val pinPhysProps: PinUI
)


// 📥📥📥 SCREEN WIDE STATE 📥📥📥
data class StateMapModel(
    val viewPort: ViewPort = ViewPort(
        mapCenter = GeoPoint(59.9343, 30.3351), // default loc, SPB
        mapZoom = 11.0,
        mapBearing = 0f
    ),
    val pins: List<PinLogical> = emptyList()
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
    private var jobViewPortStateHotUpdate: Job? = null 
    private var jobViewPortStateColdUpdate: Job? = null

    // ➡️➡️➡️ INTERACTIVE
    suspend fun saveViewPortToColdStorage() {
        val currentViewPort = _mapStateRW.value.viewPort
        mapPrefsManager.saveMapPos(currentViewPort)
    }
    fun updateViewPort(incomingViewPort: ViewPort) {
        //#1 - RAM FLUSH - QUICKER!
        jobViewPortStateHotUpdate?.cancel()
        jobViewPortStateHotUpdate = viewModelScope.launch {
            delay(200L)
            _mapStateRW.update { current ->
                current.copy(viewPort = incomingViewPort)
            }
        }
        //#2 COLD FLUSH - RELAXED!
        jobViewPortStateColdUpdate?.cancel()
        jobViewPortStateColdUpdate = viewModelScope.launch {
            delay(1000L)
            saveViewPortToColdStorage()
        }
    }

    fun constructPinQuick(incomingGeoPoint: GeoPoint): PinLogical {
        val editorHashInt = SecureRandom().nextInt(1 shl 24)
        val newPinLogical = PinLogical(
            pinLogicalId = SecureRandom().nextInt(1 shl 24),
            editorHash = byteArrayOf((editorHashInt shr 16).toByte(), (editorHashInt shr 8).toByte(), editorHashInt.toByte()),
            pinPhysProps = PinUI(
                geoPoint = incomingGeoPoint
            )
        )
        // <PUSHER W/ SIDEEFFECTS LAUNCING HERE>
        return newPinLogical
    }
    fun constructPinFull(incomingPinUI: PinUI): PinLogical {
        val editorHashInt = SecureRandom().nextInt(1 shl 24)
        val newPinLogical = PinLogical(
            pinLogicalId = SecureRandom().nextInt(1 shl 24),
            editorHash = byteArrayOf((editorHashInt shr 16).toByte(), (editorHashInt shr 8).toByte(), editorHashInt.toByte()),
            pinPhysProps = incomingPinUI
        )
        // <PUSHER W/ SIDEEFFECTS LAUNCING HERE>
        return newPinLogical
    }
    // ➡️➡️➡️ INTERACTIVE
}
// 📥📥📥 SCREEN WIDE STATE 📥📥📥





// ♻️🧭♻️🧭♻️🧭 MAP MANAGER!!! ♻️🧭♻️🧭♻️🧭
class OsmdroidManager(private val appContext: Context,                 // CLASS WRAPPER AROUND THE MapView !!!
                      private val onMapMovedCallback: (ViewPort) -> Unit, // SIMPLE CALLBACK! TO HERE WE PLACE LATER WHAT WILL UPDATE BOTH HOT + COLD!
                      private val onMapReadyCallback: suspend () -> ViewPort, // SIMPLE CALLBACK! we put cold state loading call!!! on the event afer which its safe
                      private val onTapCallback: suspend (Context, Boolean, GeoPoint) -> PinLogical, // TAPS REACTION CALLBACK <- a logical pin
                      private val onPinClick: suspend (Context) -> Unit
) {
    private val mapView: MapView // OUTSOURCED MAPVIEW !!!

    init { // FACTORY ONE TIME INSTANTIATION
        mapView = MapView(appContext).apply {
            // 🚧🚧🚧 CONFIG 🚧🚧🚧
            setTileSource(TileSourceFactory.MAPNIK )
            setMultiTouchControls(true)
            setUseDataConnection(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)

            // 🍰🍰🍰 OVERLAYS SECTION 🍰🍰🍰
            val rotationOverlay = RotationGestureOverlay(this)
            rotationOverlay.isEnabled = true
            overlays.add(rotationOverlay)
            // 🍰🍰🍰 OVERLAYS SECTION 🍰🍰🍰

            // 🤙🤙🤙 CALLBACK SECTION 🤙🤙🤙
            //  CATCH MOVEMENT -> VIEW -> MODEL (bottom -> top -> bottom) UPDATE
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
            // CATCH TAPS -> VIEW -> MODEL (bottom -> top -> bottom)
            overlays.add(MapEventsOverlay(object : MapEventsReceiver {
                override fun singleTapConfirmedHelper(geoPoint: GeoPoint?): Boolean {
                    CoroutineScope(Dispatchers.Main).launch {
                        geoPoint?.let { geoPoint ->                                                       // "if not null, do something".
                            val newHotPin = onTapCallback(appContext, false, geoPoint)
                            pushOnePinIntoPhysicalView(newHotPin)
                        }
                    }
                    return true
                }
                override fun longPressHelper(geoPoint: GeoPoint?): Boolean {
                    CoroutineScope(Dispatchers.Main).launch {
                        geoPoint?.let { geoPoint ->                                                  // "if not null, do something".
                            val newHotPin = onTapCallback(appContext, true, geoPoint)
                            pushOnePinIntoPhysicalView(newHotPin)
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
            // 🤙🤙🤙 CALLBACK SECTION 🤙🤙🤙
            // 🚧🚧🚧 CONFIG 🚧🚧🚧
        }
    }

    // 🎊🎊🎊 FUN SECTION 🎊🎊🎊
    fun getMapView(): MapView = mapView        // 🪃🪃🪃  Factory calls this 🪃🪃🪃
    fun setViewport(incomingViewPort: ViewPort) {
        mapView.controller.animateTo(
            incomingViewPort.mapCenter,
            incomingViewPort.mapZoom,
            5,
            incomingViewPort.mapBearing,
//            0  // 0ms = instant
        )
    }
    fun pushOnePinIntoPhysicalView(incomingLogicalPin: PinLogical) {
        // LOGICAL -> PHYSICAL ANGLE CONVERSION 255 -> 360
        val rotationDegrees = incomingLogicalPin.pinPhysProps.rotationByte?.let { it * 360f / 255f }

        val marker = LabeledMarker(mapView,
                        incomingLogicalPin.pinPhysProps.label ?: "",
                        rotation = rotationDegrees,
            ).apply {
            position = incomingLogicalPin.pinPhysProps.geoPoint
            textLabelBackgroundColor = 0x00000000 // TRANSPARENT (sorry for magic number, less imports)
            textLabelFontSize = 72
            setTextIcon("${incomingLogicalPin.pinPhysProps.iconUnicode}")
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)

            // redefine click action!!!
            setInfoWindow(null)
            setOnMarkerClickListener { _, _ ->
                CoroutineScope(Dispatchers.Main).launch {
                    onPinClick(appContext)
                }
                true
            }
        }
        mapView.overlays.add(marker)
        mapView.invalidate()
    }
    // 🎊🎊🎊 FUN SECTION 🎊🎊🎊
}
// ♻️🧭♻️🧭♻️🧭 MAP MANAGER!!! ♻️🧭♻️🧭♻️🧭





@Composable
fun ScreenMap(viewModel: StateMapViewModel, modifier: Modifier = Modifier) {
    val stateOfModel by viewModel.mapStateR.collectAsState()


    suspend fun showPinCreationModal(geoPoint: GeoPoint): PinUI {
        return PinUI(geoPoint = geoPoint)
    }


    AndroidView<MapView>(
        factory = { ctx ->
            OsmdroidManager(ctx,
                onMapMovedCallback = { viewModel.updateViewPort(it) },
                onMapReadyCallback = {
                    val coldViewPort = viewModel.mapPrefsManager.getInitialMapPosition()
                    viewModel.updateViewPort(coldViewPort)
                    coldViewPort        // ◀️◀️◀️ and return back... yeah
                },
                onTapCallback = { context, isLongTap, geoPoint,  ->
// 👺👺👺👺👺👺
                    if (!isLongTap) {
                        Toast.makeText(context, "SHORT: ${geoPoint.latitude}, ${geoPoint.longitude}", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "LONG: ${geoPoint.latitude}, ${geoPoint.longitude}", Toast.LENGTH_LONG).show()
                    }

                    val newPinLogical = if (isLongTap) {                // WOW val assigning through IF
                        val newPinUI = showPinCreationModal(geoPoint)  // --- FULL STOP HERE ON COROUTINE THREAD LEVEL!!! callback is of suspend type
                        viewModel.constructPinFull(newPinUI)
                    } else {
                        viewModel.constructPinQuick(geoPoint)
                    }
                    newPinLogical // ◀️◀️◀️ and return back... yeah
// 👺👺👺👺👺👺
                },
                onPinClick = { context ->
                    Toast.makeText(context, "PIN CLICKED:", Toast.LENGTH_SHORT).show()
                    // <HERE PIN CLICK CALLBACK IMPLEMENTATION>  // ◀️◀️◀️ and return back... yeah
                }
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
@file:Suppress("COMPOSE_APPLIER_CALL_MISMATCH")

package com.example.osmastic

import android.app.Application
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.TextPaint
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.retain.retain
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
// import our db/mapprefsmanager
import com.example.osmastic.db.MapPrefsManager
import kotlinx.coroutines.launch
// DATASTORE needs these to parse:
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
// manual IMPORT of MapListener - thats our emit cathcher to react! but we react debouncing . . .
// manual import for rotation support
// taps support
import org.osmdroid.views.overlay.Marker
import java.security.SecureRandom
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
//repo
import com.example.osmastic.repo.RepoPin
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
// MODALS IMPORT
import com.example.osmastic.modal.PinCreationDialog

class LabeledMarker(
    mapView: MapView,
    val pinLogicalId: Int,
    private val label: String,
    private val rotation: Float? = null,
) : Marker(mapView) {

    init {
        // 👇 NULL = upright (flat=false), VALUE = rotates with map (flat=true)
        isFlat = rotation != null
//        setAnchor(ANCHOR_CENTER, ANCHOR_BOTTOM) // overridden anyway
        rotation?.let { setRotation(it) }
    }
    private val textPaint = TextPaint().apply {
        color = Color.BLACK
        textSize = 48f
        textAlign = Paint.Align.CENTER
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
data class PinRemoveInquiry(
    val pinLogicalId: Int,
    val reachedDB: Boolean,
)
data class ViewPort(
    val mapCenter: GeoPoint, // default loc, SPB
    val mapZoom: Double, // obvious
    val mapBearing: Float,
)
data class PinUI(
    val geoPoint: GeoPoint,
    val iconUnicode: String = "📍",
    val label: String? = null,
    val rotationByte: Int? = null,
    val isHiddenBeforeTTL: Boolean = false, // 1 byte
    val hoursTTL: Int = 6, // SIX HOURS DEFAULT life time
)
data class PinLogical(
    val pinLogicalId: Int,
    val lamportEpoch: Int = 1,
    val editorHash: ByteArray,
    val expirationTimestamp: Long = 0L,  // milliseconds full epoch (built from local epoch + 1 byte hours from message) 0 = no TTL
    val pinPhysProps: PinUI,
)


// 📥📥📥 SCREEN WIDE STATE 📥📥📥
data class StateMapModel(
    val viewPort: ViewPort = ViewPort(
        mapCenter = GeoPoint(59.9343, 30.3351), // default loc, SPB
        mapZoom = 11.0,
        mapBearing = 0f
    ),
    val pins: List<PinLogical> = emptyList(), // HOT PINS!
    val pinRemoveInquiries: Set<PinRemoveInquiry> = emptySet(), // INVALID PINS IDS FOR DELAYED GC!
)
@HiltViewModel
class StateMapViewModel @Inject constructor(
    application: Application,
    val repoPin: RepoPin,
) : AndroidViewModel(application) {
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
    private fun pushOnePinLogicalToModel(incomingPinUI: PinUI): PinLogical {
        val HOUR = 3600
        val MINUTE = 60 // todo UGLY DEBUG remove later

        val editorHashInt = SecureRandom().nextInt(1 shl 24)
        val calculatedExpTimestamp = if (incomingPinUI.hoursTTL == 0) {
            0L  // eternal
        } else {
            System.currentTimeMillis() + (incomingPinUI.hoursTTL * MINUTE * 1000)
        }

        val newPinLogical = PinLogical(
            pinLogicalId = SecureRandom().nextInt(1 shl 24),
            editorHash = byteArrayOf((editorHashInt shr 16).toByte(), (editorHashInt shr 8).toByte(), editorHashInt.toByte()),
            expirationTimestamp = calculatedExpTimestamp,
            pinPhysProps = incomingPinUI
        )

        _mapStateRW.update { current ->
            current.copy(
                pins = current.pins + newPinLogical  // ← ADD TO PINS LIST
            )
        }


        // 🚚🚚🚚 SIDE EFFECTS ASYNC SECTION 🚚🚚🚚
        viewModelScope.launch {
            val validated = repoPin.pushOnePinFurther(newPinLogical)
            if (!validated) {
                val faultyPin = PinRemoveInquiry(
                    pinLogicalId = newPinLogical.pinLogicalId,
                    reachedDB = false,
                )
                _mapStateRW.update { current ->
                    current.copy(
                        pinRemoveInquiries = current.pinRemoveInquiries + faultyPin
                    )
                }
            }
        }
        // 🚚🚚🚚 SIDE EFFECTS ASYNC SECTION 🚚🚚🚚

        return newPinLogical
    }

    // AVAILABLE METHODS
    fun updateViewPort(incomingViewPort: ViewPort) { // DEBOUNCING FUNC
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
    fun constructAndPushPinQuick(geoPoint: GeoPoint) =
        pushOnePinLogicalToModel(PinUI(geoPoint))
    fun constructAndPushPinFull(pinUI: PinUI) =
        pushOnePinLogicalToModel(pinUI)


    fun collectGarbageAllLayers(incomingGarbageSnapshot: Set<PinRemoveInquiry>) {

        // #0 prep for bulk - snapshot ITS IMPORTANT to catch the state HERE
        val idsReachedDB = incomingGarbageSnapshot
            .filter { it.reachedDB }
            .map { it.pinLogicalId }
            .toSet()

        // #1 side effect run based on pinRemoveInquiry flag REPOSITORY
        viewModelScope.launch {
            repoPin.deleteBulkByLogicalIds(idsReachedDB)
        }

        //#2 remove from physical!
        //wait for pins that werent removed - wait BC THIS MIGHT BE CALLED BEFORE PIN REACHES THE VERY CANVAS


        //#3 calculate delta - WHAT WAS ACTUALLY REMOVED!

        //#4 substract MVU pins with what was REALLY REMOVED FROM ROOM + PHYSICAL

        //#5 replace all pins in pinRemoveInquiries on what WASNT REMOVED (if empty - okay)
    }


    fun replaceInvalidPinIds(incomingInquiries: Set<PinRemoveInquiry>, /*incomingReallyRemoved: Set<Int>*/) {
        _mapStateRW.update { current ->
            current.copy(
                pinRemoveInquiries = incomingInquiries,
//                pins = current.pins.filterNot { it.pinLogicalId in incomingReallyRemoved }
            )
        }
    }
    fun subtractAllPins(incomingInvalidPinsIds: Set<Int>) {
        _mapStateRW.update { current ->
            current.copy(
                pins = current.pins.filterNot { it.pinLogicalId in incomingInvalidPinsIds }
            )
        }
    }



    // from cold and bulk, thats the idea for this one for now.
    fun replaceAllPins(incomingPins: List<PinLogical>) {
        _mapStateRW.update { current ->
            current.copy(pins = incomingPins)
        }
    }

    // ➡️➡️➡️ INTERACTIVE
}
// 📥📥📥 SCREEN WIDE STATE 📥📥📥











@Composable
fun ScreenMap(viewModel: StateMapViewModel, modifier: Modifier = Modifier) {
    val stateOfModel by viewModel.mapStateR.collectAsState()
    var showDialog by remember { mutableStateOf(false) }
    var dialogGeoPoint by remember { mutableStateOf<GeoPoint?>(null) }
    var dialogContinuation by remember { mutableStateOf<Continuation<PinUI>?>(null) }

    suspend fun showPinCreationModal(geoPoint: GeoPoint): PinUI = suspendCoroutine { cont ->
        showDialog = true
        dialogGeoPoint = geoPoint
        dialogContinuation = cont  // ← saves the waiting coroutine
    }

    val ctx = LocalContext.current

//    val osmdroidManager = remember {
    val osmdroidManager = retain { // TODO remember or retain now? old navigation 1
        OsmdroidManager( ctx,
            onMapMovedCallback = { viewModel.updateViewPort(it) },
            onMapReadyViewPortCallback = {
                val coldViewPort = viewModel.mapPrefsManager.getInitialMapPosition()
                viewModel.updateViewPort(coldViewPort)
                coldViewPort        // ◀️◀️◀️ and return back... yeah
            },
            onMapReadyInitialPinsCallback = {
                val coldPins = viewModel.repoPin.getAllPins()
                viewModel.replaceAllPins(coldPins)
                coldPins            // ◀️◀️◀️ and return back... yeah
            },
            onTapShortCallback = { context, geoPoint,  ->
                Toast.makeText(context, "SHORT: ${geoPoint.latitude}, ${geoPoint.longitude}", Toast.LENGTH_SHORT).show() // TODO delete debug toasts
                viewModel.constructAndPushPinQuick(geoPoint) // ◀️◀️◀️ and return back... yeah
            },
            onTapLongCallback = { context, geoPoint ->
                Toast.makeText(context, "LONG: ${geoPoint.latitude}, ${geoPoint.longitude}", Toast.LENGTH_SHORT).show() // TODO delete debug toasts
                val newPinUI = showPinCreationModal(geoPoint)  // 🛑🛑🛑  --- FULL STOP HERE ON COROUTINE THREAD LEVEL!!! callback is of suspend type 🛑🛑🛑
                viewModel.constructAndPushPinFull(newPinUI)  // ◀️◀️◀️ and return back... yeah
            },
            onPinClick = { context ->
                Toast.makeText(context, "PIN CLICKED:", Toast.LENGTH_SHORT).show() // TODO delete debug toasts
                // <HERE PIN CLICK CALLBACK IMPLEMENTATION>  // ◀️◀️◀️ and return back... yeah
            }
        )
    }

    // 🎣🎣🎣 EFFECTS BLOCK 🎣🎣🎣
    // TOP -> BOTTOM REACTION on MVU STATE CHANCGED GRANULAR = invalidPinIds
    LaunchedEffect(stateOfModel.pinRemoveInquiries) {

        if (stateOfModel.pinRemoveInquiries.isNotEmpty()) {
            delay(5000) //  TODO DEBUG, visualising, remove later

            val pinRemoveInquiriesSnapshot = stateOfModel.pinRemoveInquiries

            // #0 prep for bulk - snapshot ITS IMPORTANT to catch the state HERE
            val idsReachedDB = pinRemoveInquiriesSnapshot
                .filter { it.reachedDB }
                .map { it.pinLogicalId }
                .toSet()

            // #1 side effect run based on pinRemoveInquiry flag REPOSITORY


            //#2 remove from physical!
            //wait for pins that werent removed (erros? who cares)

            //#3 calculate delta - WHAT WAS ACTUALLY REMOVED!

            //#4 substract MVU pins with what was REALLY REMOVED FROM ROOM + PHYSICAL

            //#5 replace all pins in pinRemoveInquiries on what WASNT REMOVED (if empty - okay)

//            val couldNotRemove = osmdroidManager.doGarbageCollect(pinRemoveInquiries)
//            val wereReallyRemovedDelta = pinRemoveInquiries - couldNotRemove
//            viewModel.replaceInvalidPinIds(couldNotRemove)
        }
        Toast.makeText(ctx, "GC", Toast.LENGTH_SHORT).show()
    }



    LaunchedEffect(Unit) {
        while(true) {
            delay(60_000) // Every minute
            val nowTimestamp = System.currentTimeMillis()

            val expiredIds = stateOfModel.pins
                .filter { pin ->
                    pin.expirationTimestamp != 0L && // SKIP ETERNAL PINS 0L (long)
                            pin.expirationTimestamp <= nowTimestamp
                }
                .map { it.pinLogicalId }
                .toSet()

            if (expiredIds.isNotEmpty()) {
//                viewModel.updateInvalidPinIds(expiredIds, Null) // Reuse existing GC!
            }
        }
    }




    // 🎣🎣🎣 EFFECTS BLOCK 🎣🎣🎣

    AndroidView<MapView>(
        factory = { ctx ->
            osmdroidManager.getMapView()
        },
        update = { /* nothing here - not using native MVVM updates */
            Toast.makeText(ctx, "UPDATE CALLBACK", Toast.LENGTH_SHORT).show()
        }
    )




    //DEBUG // TODO move somewhere smarter
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
            Text("${stateOfModel.pinRemoveInquiries}", fontSize = 15.sp)
            Text("${stateOfModel.pins}", fontSize = 10.sp)
//            Text("Center: ${viewModel.uiState.mapCenter.latitude}, ${viewModel.uiState.mapCenter.longitude}")

        }
    } // Box end

    // ON RECOMPOSE + showDialog == true - MODAL APPEARS
    if (showDialog && dialogGeoPoint != null) {
        PinCreationDialog(
            geoPoint = dialogGeoPoint!!,
            onConfirm = { pinUI ->
                showDialog = false
                dialogContinuation?.resume(pinUI)
                dialogContinuation = null
            },
            onDismiss = {
                showDialog = false
                dialogContinuation?.resume(PinUI(geoPoint = dialogGeoPoint!!))
                dialogContinuation = null
            }
        )
    } // if end

} // ScreenMap end


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
import com.example.osmastic.modal.PinEditDialog
import kotlin.math.cos
import kotlin.math.sin

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
            val gapPx = 25 * mapView.resources.displayMetrics.density
//            // LABEL ALWAYS FLAT (counter-rotate)
            canvas.save()
            canvas.rotate(-mapView.mapOrientation, point.x.toFloat(), point.y + gapPx)
            canvas.drawText(label, point.x.toFloat(), point.y + gapPx, textPaint)
            canvas.restore()
//
//            // DELETE THESE UNDERNEATH vvvvv
//            // EXPERIMENTAL
//            val rotationRad = Math.toRadians((rotation ?: 0f).toDouble())
//            val offsetX = (gapPx * sin(rotationRad)).toFloat()
//            val offsetY = (gapPx * cos(rotationRad)).toFloat()
//
//            canvas.save()
//            canvas.rotate(-mapView.mapOrientation, point.x + offsetX, point.y + offsetY)
//            canvas.drawText(label, point.x + offsetX, point.y + offsetY, textPaint)
//            canvas.restore()
        }
    }
}

data class PinRemoveInquiry(
    val pinLogicalId: Int,
    val reachedDB: Boolean,
)

data class ViewPort( // CUSTOM VIEWPORT FOR OSMD!
    val mapCenter: GeoPoint, // default loc, SPB
    val mapZoom: Double, // obvious
    val mapBearing: Float,
)
data class PinUI(
    val geoPoint: GeoPoint,
    val rotationByte: Int? = null,
    val iconUnicode: String = "📍",
//    val label: String? = null, // STOP BEING NULL FOR THE PROTOBUF SAKE!
    val label: String = "",
    val isHiddenBeforeTTL: Boolean = false, // 1 byte
    val minutesTTL: Int = 1, // SIX MINUTES DEFAULT life time
)
data class PinLogical(
    val pinLogicalId: Int,
//    val editorHash: ByteArray,
    val editorMark: String,
    val lamportEpoch: Int = 1,
    val expirationTimestamp: Long = 0L,  // milliseconds full epoch (built from local epoch + 1 byte hours from message) 0 = no TTL
    val pinPhysProps: PinUI,
)

// 📥📥📥 SCREEN WIDE STATE 📥📥📥
data class StateMapModel (
    val viewPort: ViewPort = ViewPort(
        mapCenter = GeoPoint(59.9343, 30.3351), // default loc, SPB
        mapZoom = 11.0,
        mapBearing = 0f
    ),
    val pins: Set<PinLogical> = emptySet(), // HOT PINS!
    val pinRenderInquiries: Set<PinLogical> = emptySet(),      // LaunchedEffect MUST be able to observe DELTAS
    val pinRemoveInquiries: Set<PinRemoveInquiry> = emptySet(), // INVALID PINS IDS FOR DELAYED GC!
    val pinUpdateInquiries: Set<PinLogical> = emptySet(), // TODO EXPERIMENTAL update pin inquiries to trigger LAUNCHED EFFECT TO call OSMD callback to: GC + PUSH.
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

    // HANG CALLBACKS' IMPLEMENTATIONS here onto the repoPin instance. STUPID and BEAUTIFUL
    init {
        repoPin.onHandledPinCreationRequestCallback = { builtPinLogicalNew ->
            pushNewPinFromTop(builtPinLogicalNew)
        }
        repoPin.onHandledPinUpdateRequestCallback = { builtPinLogicalNew ->
            updatePinFromTop(builtPinLogicalNew)
        }
    }

    // ➡️➡️➡️ INTERACTIVE
    private suspend fun saveViewPortToColdStorage() {
        val currentViewPort = _mapStateRW.value.viewPort
        mapPrefsManager.saveMapPos(currentViewPort)
    }
    private fun pushNewPinFromBottom(incomingPinUI: PinUI): PinLogical {
        //#0 prep data
        val calculatedExpTimestamp = if (incomingPinUI.minutesTTL == 0) {
            0L  // eternal
        } else {
            val SECOND_IN_MIL = 1000
            val MINUTE_IN_SEC = 60
            System.currentTimeMillis() + (incomingPinUI.minutesTTL * MINUTE_IN_SEC * SECOND_IN_MIL)
        }
        // WHY 4 UTF-8? for 65k chance for collision. 2 bytes for teh same chance only possible via custom byte array protocol
        val fetchedEditorHash = repoPin.portalToMesh.serviceConnectionWrapper.getUniqueNodeIdMark()?.takeLast(4) ?: "local"

        //#1 construct a new pin
        val newPinLogical = PinLogical(
//            pinLogicalId = SecureRandom().nextInt(1 shl 24),
            pinLogicalId = SecureRandom().nextInt(1 shl 28),
            editorMark = fetchedEditorHash,
            expirationTimestamp = calculatedExpTimestamp,
            pinPhysProps = incomingPinUI
        )

        //#2 MVU UPDATE
        _mapStateRW.update { current ->
            current.copy(
                pins = current.pins + newPinLogical  // ← ADD TO PINS LIST
            )
        }

        //#3 COLD+RADIO UPDATE
        // 🚚🚚🚚 SIDE EFFECTS ASYNC SECTION 🚚🚚🚚
        viewModelScope.launch {
//            val validated = repoPin.pushOnePinFurther(newPinLogical)
            val validated = repoPin.pushPinFurther(null, newPinLogical)

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

        //#4 PHYSICAL UPDATE (return what we made and let it finish the sync)
        return newPinLogical
    }

    fun updatePinFromBottom(oldFoundPinLogical: PinLogical, updatedPinPhysProps: PinUI, ): PinLogical {
        //#0 prep data
        //#1 update MVU
        //#2 launch SIDE EFFECTS (repo)
        //#999 return NEW pin LOGICAL to visual/physical

        //#0 prep data // WHY 4 UTF-8? for 65k chance for collision. 2 bytes for teh same chance only possible via custom byte array protocol
        val fetchedEditorHash = repoPin.portalToMesh.serviceConnectionWrapper.getUniqueNodeIdMark()?.takeLast(4) ?: "local"

        val newPinLogicalHalfBaked = PinLogical(
            pinLogicalId = oldFoundPinLogical.pinLogicalId,
            lamportEpoch = oldFoundPinLogical.lamportEpoch + 1,
            editorMark = fetchedEditorHash, //oldFoundPinLogical.editorHash,
            expirationTimestamp = oldFoundPinLogical.expirationTimestamp,
            pinPhysProps = updatedPinPhysProps,
        )

        val SECOND_IN_MIL = 1000
        val MINUTE_IN_SEC = 60
        // we push to repo a pin logical object with recaulated TTL so EACH node could recreate PIN if missed initial one
        // better convergence on drifted away clocks!
        val recalculatedMinutesTTL = when {
            newPinLogicalHalfBaked.expirationTimestamp == 0L -> 0  // eternal
            else -> {
                val remaining = ((newPinLogicalHalfBaked.expirationTimestamp - System.currentTimeMillis()) / (SECOND_IN_MIL * MINUTE_IN_SEC) ).toInt()
                remaining.coerceIn(1, 16383)  // varint KILLER SWITCH (not more than 2 bytes payload)
            }
        }

        val newPinLogical = newPinLogicalHalfBaked.copy(
            pinPhysProps = newPinLogicalHalfBaked.pinPhysProps.copy(
                minutesTTL = recalculatedMinutesTTL // MINUTES FROM NOW ON | two bytes max
            )
        )

        //#1 update MVU
        _mapStateRW.update { current ->
            val pinsReconstructed = current.pins.mapTo(mutableSetOf()) { oldPinLogical ->
                if (oldPinLogical.pinLogicalId == newPinLogical.pinLogicalId) {
                    newPinLogical
                } else {
                    oldPinLogical
                }
            }
            current.copy(
                pins = pinsReconstructed,
                pinUpdateInquiries = current.pinUpdateInquiries + newPinLogical,
            )
        }

        //#2 launch SIDE EFFECTS (repo)
        // 🚚🚚🚚 SIDE EFFECTS ASYNC SECTION 🚚🚚🚚
        viewModelScope.launch {
//            val validated = repoPin.pushOneDeltaFurther(oldFoundPinLogical,newPinLogical)
            val validated = repoPin.pushPinFurther(oldFoundPinLogical, newPinLogical)

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

        //#999 return NEW pin LOGICAL to visual/physical
        return newPinLogical
    }

    private fun pushNewPinFromTop(incomingPinLogical: PinLogical) {
        _mapStateRW.update { current ->
            current.copy(
                pins = current.pins + incomingPinLogical,
                pinRenderInquiries = current.pinRenderInquiries + incomingPinLogical,
            )
        }
    }
    private fun updatePinFromTop(incomingPinLogical: PinLogical) {
        _mapStateRW.update { current ->
            current.copy(
                pins = current.pins.mapTo(mutableSetOf()) { pin ->
                    if (pin.pinLogicalId == incomingPinLogical.pinLogicalId) {
                        incomingPinLogical // replace with updated version
                    } else {
                        pin // keep existing
                    }
                },
                pinUpdateInquiries = current.pinUpdateInquiries + incomingPinLogical,
            )
        }
    }

    // 🧩🧩🧩 AVAILABLE METHODS
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
        pushNewPinFromBottom(PinUI(geoPoint))
    fun constructAndPushPinFull(pinUI: PinUI) =
        pushNewPinFromBottom(pinUI)
    fun replaceInvalidPinIds(incomingInquiries: Set<PinRemoveInquiry>) { //TODO - REDO WITH SUBTRACTING!!!!
        _mapStateRW.update { current ->
            current.copy(
                pinRemoveInquiries = incomingInquiries,
            )
        }
    }
    fun replacePins(incomingPins: Set<PinLogical>) {
        _mapStateRW.update { current ->
            current.copy(pins = incomingPins)
        }
    }
    fun subtractFromPins(incomingInvalidPinIds: Set<Int>) {
        _mapStateRW.update { current ->
            current.copy(
                pins = current.pins.filterTo(mutableSetOf()) {
                    it.pinLogicalId !in incomingInvalidPinIds
                }
            )
        }
    }
    fun subtractFromRenderInquiries(incomingRenderedPins: Set<PinLogical>) {
        _mapStateRW.update { current ->
            current.copy(
                pinRenderInquiries = current.pinRenderInquiries - incomingRenderedPins
            )
        }
    }
    fun subtractFromUpdateInquiries(incomingUpdatedPins: Set<PinLogical>) {
        _mapStateRW.update { current ->
            current.copy(
                pinUpdateInquiries = current.pinUpdateInquiries - incomingUpdatedPins
            )
        }
    }
    // 🧩🧩🧩 AVAILABLE METHODS
    // ➡️➡️➡️ INTERACTIVE
}
// 📥📥📥 SCREEN WIDE STATE 📥📥📥











@Composable
fun ScreenMap(viewModel: StateMapViewModel, modifier: Modifier = Modifier) {
    val stateOfModel by viewModel.mapStateR.collectAsState()
    var showDialog by remember { mutableStateOf(false) }
    var dialogGeoPoint by remember { mutableStateOf<GeoPoint?>(null) } // thats to teleport geopoint to the CREATION dialog! SCREENMAP -> MODAL
    var dialogPinUI by remember { mutableStateOf<PinUI?>(null) } // thats to teleport geopoint to the UPDATE dialog!        SCREENMAP -> MODAL
    var dialogContinuation by remember { mutableStateOf<Continuation<PinUI>?>(null) }           // thats our teleport       MODAL -> SCREENMAP

    suspend fun showPinCreationModal(geoPoint: GeoPoint): PinUI = suspendCoroutine { cont ->
        showDialog = true
        dialogGeoPoint = geoPoint
        dialogPinUI = null
        dialogContinuation = cont  // ← saves the waiting coroutine
    }

    suspend fun showPinUpdateModal(pinUI: PinUI): PinUI = suspendCoroutine { cont ->
        showDialog = true
        dialogGeoPoint = null
        dialogPinUI = pinUI
        dialogContinuation = cont
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
                viewModel.replacePins(coldPins)
                coldPins            // ◀️◀️◀️ and return back... yeah
            },
            onTapShortCallback = { context, geoPoint,  ->
//                val generatedPin = viewModel.constructAndPushPinQuick(geoPoint)
//                Toast.makeText(context, "SHORT: ${generatedPin.editorHash}", Toast.LENGTH_SHORT).show() // TODO delete debug toasts
//                val something = viewModel.repoPin.portalToMesh.serviceConnectionWrapper.getPrimaryChannelPsk() //TODO REMOVE, DEBUG
//                val something2 = viewModel.repoPin.portalToMesh.serviceConnectionWrapper.getUniqueNodeIdMark()
////                Log.d("ASS", channels.toString())
//                Log.d("ASS", "ass? --- ${something2}")
                viewModel.constructAndPushPinQuick(geoPoint) // ◀️◀️◀️ and return back... yeah
//                generatedPin
            },
            onTapLongCallback = { context, geoPoint ->
//                Toast.makeText(context, "LONG: ${geoPoint.latitude}, ${geoPoint.longitude}", Toast.LENGTH_SHORT).show() // TODO delete debug toasts
                val newPinUI = showPinCreationModal(geoPoint)  // 🛑🛑🛑  --- FULL STOP HERE ON COROUTINE THREAD LEVEL!!! callback is of suspend type 🛑🛑🛑
                viewModel.constructAndPushPinFull(newPinUI)  // ◀️◀️◀️ and return back... yeah
            },
            onPinClick = { context, pinLogicalId ->

                stateOfModel.pins.find { it.pinLogicalId == pinLogicalId }?.let { foundPin ->
                    val updatedPinUI = showPinUpdateModal(foundPin.pinPhysProps)    // 🛑🛑🛑  --- FULL STOP HERE ON COROUTINE THREAD LEVEL!!! callback is of suspend type 🛑🛑🛑
                    viewModel.updatePinFromBottom(foundPin, updatedPinUI) // ◀️◀️◀️ and return back... yeah
                } ?: run {
                    // NO PIN FOUND!!! manual sync failed . . .  - trigger GC or trust our pipelines? return null either way
                    null                                    // ◀️◀️◀️ and return back... yeah
                }
            }
        )
    }

    // 🎣🎣🎣 EFFECTS BLOCK 🎣🎣🎣
    // ♻️♻️♻️ GC ♻️♻️♻️
    // TOP -> BOTTOM REACTION on MVU STATE CHANCGED GRANULAR = invalidPinIds
    LaunchedEffect(stateOfModel.pinRemoveInquiries) {

        if (stateOfModel.pinRemoveInquiries.isNotEmpty()) {
            delay(5000) //  TODO DEBUG, visualising, remove later

            // #0 prep for bulk - snapshot ITS IMPORTANT to catch the state HERE
            val pinRemoveInquiriesSnapshot = stateOfModel.pinRemoveInquiries

            //#1 remove from physical FIRST! WHY? bc physical is most unreliable in
            //our case! ROOM is local sqlite3 file, super relibale
            //wait for pins that werent removed - potential RACE CONDITION SAVE
            val invalidIds = pinRemoveInquiriesSnapshot // fetch IDS only from this set of objects, stupid really!
                .map { it.pinLogicalId }
                .toSet()
            val couldNotRemoveIds = osmdroidManager.doGarbageCollect(invalidIds)
            val couldNotRemoveObj = pinRemoveInquiriesSnapshot
                .filter { it.pinLogicalId in couldNotRemoveIds } // those objects, that were NOT REMOVED as it seems!
                .toSet()

            //OPTIONAL!
            //#2 side effect run based on pinRemoveInquiry flag REPOSITORY
            val idsReachedDB = pinRemoveInquiriesSnapshot
                .filter { it.reachedDB }
                .map { it.pinLogicalId }
                .toSet()
            if (idsReachedDB.isNotEmpty()) {
                viewModel.repoPin.deleteBulkByLogicalIds(idsReachedDB) // STINGER bc room is reliable
            }

            //#3 calculate delta - WHAT WAS ACTUALLY REMOVED!
            val wereReallyRemovedIds = invalidIds - couldNotRemoveIds

            //#4 substract MVU pins with what was REALLY REMOVED FROM ROOM + PHYSICAL
            viewModel.subtractFromPins(wereReallyRemovedIds)

            //#5 replace all pins in pinRemoveInquiries on what WASNT REMOVED (if empty - okay)
            viewModel.replaceInvalidPinIds(couldNotRemoveObj)
        }
//        Toast.makeText(ctx, "GC", Toast.LENGTH_SHORT).show() //TODO GC toast
    }
    // ♻️♻️♻️ TIMESTAMP INVALIDATOR ♻️♻️♻️
    LaunchedEffect(Unit) {
        while(true) {
            delay(10_000) // TODO: delay too short (10 sec), debug. increase to 5 minutes.
            val nowTimestamp = System.currentTimeMillis()

            // make Set of inquiries from pin.pinLogicalId of those pins that are expired!
            val expiredInquiries = stateOfModel.pins
                .filter { pin ->
                    pin.expirationTimestamp != 0L &&
                            pin.expirationTimestamp <= nowTimestamp
                }
                .map {
                    PinRemoveInquiry(
                        pinLogicalId = it.pinLogicalId,
                        reachedDB = true
                    )
                }
                .toSet()

            if (expiredInquiries.isNotEmpty()) {
                // Get current inquiries, add new ones, replace all
                val currentInquiries = stateOfModel.pinRemoveInquiries
                val updatedInquiries = currentInquiries + expiredInquiries
                viewModel.replaceInvalidPinIds(updatedInquiries)
            }
//            Toast.makeText(ctx, "INVALIDATOR $nowTimestamp", Toast.LENGTH_SHORT).show() //TODO: invalidator TOAST
        }
    }
    LaunchedEffect(stateOfModel.pinRenderInquiries) { // RENDER!

        if (stateOfModel.pinRenderInquiries.isNotEmpty()) {
            val pinRenderInquiriesSnapshot = stateOfModel.pinRenderInquiries // SNAPSHOT OF THE STATE!
            osmdroidManager.pushManyPinsIntoPhysicalView(pinRenderInquiriesSnapshot) // RENDER!
            viewModel.subtractFromRenderInquiries(pinRenderInquiriesSnapshot) // SUBTRACT SNAPSHOT FROM CURRENT STATE!

            Toast.makeText(ctx, "LE RADIO -> BOTTOM", Toast.LENGTH_SHORT).show()
        }

    }
    LaunchedEffect(stateOfModel.pinUpdateInquiries) { // RE-RENDER!

        if (stateOfModel.pinUpdateInquiries.isNotEmpty()) {
            val pinUpdateInquiriesSnapshot = stateOfModel.pinUpdateInquiries // SNAPSHOT OF THE STATE!
            osmdroidManager.updateManyPinsInsidePhysicalView(pinUpdateInquiriesSnapshot)// RE-RENDER!
            viewModel.subtractFromUpdateInquiries(pinUpdateInquiriesSnapshot) // SUBTRACT SNAPSHOT FROM CURRENT STATE!

            Toast.makeText(ctx, "LE RADIO -> BOTTOM UPDATE!!!", Toast.LENGTH_SHORT).show()
        }

    }
    // 🎣🎣🎣 EFFECTS BLOCK 🎣🎣🎣

    AndroidView<MapView>(
        factory = { ctx ->
            osmdroidManager.getMapView()
        },
        update = { /* nothing here - not using native MVVM updates */
//            Toast.makeText(ctx, "UPDATE CALLBACK", Toast.LENGTH_SHORT).show() // TODO: UPDATE ANDROID VIEW CALLBACK toast
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
            Text("${stateOfModel.pinRenderInquiries}", fontSize = 15.sp)
            Text("${stateOfModel.pins}", fontSize = 8.sp)
        }
    } // Box end

    // ON RECOMPOSE + showDialog == true - MODAL APPEARS
    if (showDialog) {
        when {
            dialogGeoPoint != null -> {
                PinEditDialog(
                    geoPoint = dialogGeoPoint!!,
                    existingPinUI = null,
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
            }
            dialogPinUI != null -> {
                PinEditDialog(
                    geoPoint = null,
                    existingPinUI = dialogPinUI,
                    onConfirm = { pinUI ->
                        showDialog = false
                        dialogContinuation?.resume(pinUI)
                        dialogContinuation = null
                    },
                    onDismiss = {
                        showDialog = false
                        dialogContinuation?.resume(dialogPinUI!!)  // return ORIGNAL??? on dismiss
                        dialogContinuation = null
                    }
                )
            }
        }
    }

} // ScreenMap end


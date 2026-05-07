package com.example.osmastic

import android.app.Application
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.TextPaint
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.osmastic.db.MapPrefsManager
import com.example.osmastic.repo.RepoPin
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.security.SecureRandom
import kotlin.collections.plus

class LabeledMarker(
    mapView: MapView,
    val pinLogicalId: Int,
    private val label: String,
    private val rotation: Float? = null,
) : Marker(mapView) {

    init {
        // NULL = upright (flat=false), VALUE = rotates with map (flat=true)
        isFlat = rotation != null
//        setAnchor(ANCHOR_CENTER, ANCHOR_BOTTOM) // overridden anyway
        rotation?.let { setRotation(it) }
    }
    private val textPaintIcon = TextPaint().apply {
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
            canvas.drawText(label, point.x.toFloat(), point.y + gapPx, textPaintIcon)
            canvas.restore()
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

        val newerPinLogical = newPinLogicalHalfBaked.copy(
            pinPhysProps = newPinLogicalHalfBaked.pinPhysProps.copy(
                minutesTTL = recalculatedMinutesTTL // MINUTES FROM NOW ON | two bytes max
            )
        )

        //#1 update MVU
        _mapStateRW.update { current ->
            val pinsUpdated = current.pins.mapTo(mutableSetOf()) { currentOldPin ->
                if (currentOldPin.pinLogicalId == newerPinLogical.pinLogicalId) {
                    newerPinLogical
                } else {
                    currentOldPin
                }
            }
            current.copy(
                pins = pinsUpdated,
                //todo НУЖНО ПЕРЕХОДИТЬ НА ШИНУ ЗДЕСЬ ТОЖЕ, реакция от osmdroid не ДОЛЖНА ЖДАТЬ ОТВЕТА, не двунаправленный поток, а два однонаправленных нужно!
//                pinUpdateInquiries = current.pinUpdateInquiries + newPinLogical,
            )
        }

        //#2 launch SIDE EFFECTS (repo)
        // 🚚🚚🚚 SIDE EFFECTS ASYNC SECTION 🚚🚚🚚
        viewModelScope.launch {
//            val validated = repoPin.pushOneDeltaFurther(oldFoundPinLogical,newPinLogical)
            val validated = repoPin.pushPinFurther(oldFoundPinLogical, newerPinLogical)

            if (!validated) {

                // TODO ПРАВИЛЬНАЯ ПЕРЕДЕЛКА ТУТ: надо так же делать выше!
                _mapStateRW.update { current ->
                    val pinsRolledBack = current.pins.mapTo(mutableSetOf()) { currentPin ->
                        if (currentPin.pinLogicalId == oldFoundPinLogical.pinLogicalId) {
                            oldFoundPinLogical
                        } else {
                            currentPin
                        }
                    }
                    current.copy(
                        pins = pinsRolledBack,
                        pinUpdateInquiries = current.pinUpdateInquiries + oldFoundPinLogical,
                    )
                }

//                val faultyPin = PinRemoveInquiry(
//                    pinLogicalId = newPinLogical.pinLogicalId,
//                    reachedDB = false,
//                )
//                _mapStateRW.update { current ->
//                    current.copy(
//                        pinRemoveInquiries = current.pinRemoveInquiries + faultyPin
//                    )
//                }

            } // !validated finish
        }
        // 🚚🚚🚚 SIDE EFFECTS ASYNC SECTION 🚚🚚🚚

        //#999 return NEW pin LOGICAL to visual/physical
        return newerPinLogical
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


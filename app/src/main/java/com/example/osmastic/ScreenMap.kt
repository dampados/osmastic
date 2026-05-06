@file:Suppress("COMPOSE_APPLIER_CALL_MISMATCH")

package com.example.osmastic

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
//new ones:
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.Feed
import androidx.compose.material.icons.automirrored.filled.Grading
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.NotListedLocation
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.Wysiwyg
import androidx.compose.material.icons.automirrored.outlined.ArrowBackIos
import androidx.compose.material.icons.automirrored.rounded.AddToHomeScreen
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.material.icons.filled.ScreenRotationAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.retain.retain
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.osmastic.modal.ModalRenderer
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import kotlinx.coroutines.delay
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
// MODALS IMPORT
import com.example.osmastic.modal.PinEditDialog

@Composable
fun ScreenMap(viewModel: StateMapViewModel, modifier: Modifier = Modifier) {
    val stateOfModel by viewModel.mapStateR.collectAsState() // whole data state of the MAP and PINS (not ui)
    val uiModalManager = remember { StateUIViewModel() } // mini thingie for the UI, modals, continuation, etc.

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
//                uiModalManager.openPinsList()
//                Log.d("ass", "PIN LIST OPEN")
                viewModel.constructAndPushPinQuick(geoPoint) // ◀️◀️◀️ and return back... yeah
            },
            onTapLongCallback = { context, geoPoint ->
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
    } // OLD ROUTER

    Box(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(6.dp)
                .navigationBarsPadding()
                .imePadding(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val ROUNDED_PERCENT = 32
            val ICON_SIZE = 23

            Button(
                onClick = { uiModalManager.openPinsList() },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(ROUNDED_PERCENT)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Grading,
                    contentDescription = null,
                    modifier = Modifier.size(ICON_SIZE.dp)
                )
            }
            Button(
                onClick = { /* TODO: channels */ },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(ROUNDED_PERCENT)
            ) {
                Icon(
                    imageVector = Icons.Default.SatelliteAlt,
                    contentDescription = null,
                    modifier = Modifier.size(ICON_SIZE.dp)
                )
            }
            Button(
                onClick = { },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(ROUNDED_PERCENT)
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = null,
                    modifier = Modifier.size(ICON_SIZE.dp)
                )
            }
            Button(
                onClick = {
                    // todo ТАК НЕЛЬЗЯ ПЕРЕДЕЛАТЬ НА ШИНЫ СОБЫТИЙ ДЛЯ ПЕРВОГО ЧТЕНИЯ
                    // todo ТАК НЕЛЬЗЯ ПЕРЕДЕЛАТЬ КНОПКУ НА Launched Effect по изменению СТЕЙТА
                    osmdroidManager.setViewport(stateOfModel.viewPort.copy(mapBearing = 0.0f))
                    viewModel.updateViewPort(stateOfModel.viewPort.copy(mapBearing = 0.0f))
                          },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(ROUNDED_PERCENT)
            ) {
                Icon(
                    imageVector = Icons.Default.ScreenRotationAlt,
                    contentDescription = null,
                    modifier = Modifier.size(ICON_SIZE.dp)
                )
            }
        }
    }

    ModalRenderer(uiModalManager, stateOfModel.pins)

    //                uiModalManager.openPinsList()
    //                Log.d("ass", "PIN LIST OPEN")


} // ScreenMap end


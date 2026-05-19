@file:Suppress("COMPOSE_APPLIER_CALL_MISMATCH")

package com.example.osmastic

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Grading
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.DeveloperBoardOff
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.material.icons.filled.ScreenRotationAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.retain.retain
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.osmastic.modal.ModalRenderer
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import kotlinx.coroutines.delay
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
// MODALS IMPORT
import com.example.osmastic.modal.PinEditDialog
import kotlinx.coroutines.flow.update

@Composable
fun ScreenMap(viewModel: StateMapViewModel, modifier: Modifier = Modifier) {
    val mapStateCollected by viewModel.mapStateR.collectAsState() // whole data state of the MAP and PINS (not ui)
    val uiStateManager = remember { StateUIViewModel() } // mini thingie for the UI, modals, continuation, etc.
    val uiStateCollected by uiStateManager.uiStateR.collectAsState()

    var showDialog by remember { mutableStateOf(false) }
    var dialogGeoPoint by remember { mutableStateOf<GeoPoint?>(null) } // thats to teleport geopoint to the CREATION dialog! SCREENMAP -> MODAL
    var dialogPinUI by remember { mutableStateOf<PinUI?>(null) } // thats to teleport geopoint to the UPDATE dialog!        SCREENMAP -> MODAL
    var dialogContinuation by remember { mutableStateOf<Continuation<PinUI>?>(null) }           // thats our teleport       MODAL -> SCREENMAP
    var clickedPinLogicalId by remember { mutableStateOf<Int?>(null) }

    var showDebug by remember { mutableStateOf<Boolean>(value = false)  }

    suspend fun showPinCreationModal(geoPoint: GeoPoint): PinUI = suspendCoroutine { cont ->
        showDialog = true
        dialogGeoPoint = geoPoint
        dialogPinUI = null
        dialogContinuation = cont  // ← saves the waiting coroutine
    }

    suspend fun showPinUpdateModal(pinUI: PinUI, pinLogicalId: Int): PinUI = suspendCoroutine { cont ->
        showDialog = true
        dialogGeoPoint = null
        dialogPinUI = pinUI
        dialogContinuation = cont
        clickedPinLogicalId = pinLogicalId
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
            onTapShortCallback = { _, geoPoint,  ->
//                uiModalManager.openPinsList()
//                Log.d("ass", "PIN LIST OPEN")
                viewModel.constructAndPushPinQuick(geoPoint) // ◀️◀️◀️ and return back... yeah
            },
            onTapLongCallback = { context, geoPoint ->
                val newPinUI = showPinCreationModal(geoPoint)  // 🛑🛑🛑  --- FULL STOP HERE ON COROUTINE THREAD LEVEL!!! callback is of suspend type 🛑🛑🛑
                viewModel.constructAndPushPinFull(newPinUI)  // ◀️◀️◀️ and return back... yeah
            },
            onPinClick = { _, pinLogicalId ->

                mapStateCollected.pins.find { it.pinLogicalId == pinLogicalId }?.let { foundPin ->
                    val updatedPinUI = showPinUpdateModal(foundPin.pinPhysProps, pinLogicalId)    // 🛑🛑🛑  --- FULL STOP HERE ON COROUTINE THREAD LEVEL!!! callback is of suspend type 🛑🛑🛑
                    viewModel.updatePinFromBottom(foundPin, updatedPinUI) // ◀️◀️◀️ and return back... yeah
                } ?: run {
                    // NO PIN FOUND!!! manual sync failed . . .  - trigger GC or trust our pipelines? return null either way
                    null                                    // ◀️◀️◀️ and return back... yeah
                }
            },
            onMarkerMovedCallback = { movedPinLogicalID, newGeoPoint ->
                val newMoveInquiry = Inquiry.PinMoveInquiry(movedPinLogicalID, newGeoPoint)
                viewModel.addInquiry(newMoveInquiry)
            }
        )
    }

    // 🎣🎣🎣 EFFECTS BLOCK 🎣🎣🎣
    // ♻️♻️♻️ GC ♻️♻️♻️
    // TOP -> BOTTOM REACTION on MVU STATE CHANCGED GRANULAR = invalidPinIds
    LaunchedEffect(mapStateCollected.pinRemoveInquiries) {

        if (mapStateCollected.pinRemoveInquiries.isNotEmpty()) {
            delay(5000) //  TODO DEBUG, visualising, remove later

            // #0 prep for bulk - snapshot ITS IMPORTANT to catch the state HERE
            val pinRemoveInquiriesSnapshot = mapStateCollected.pinRemoveInquiries

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
            val expiredInquiries = mapStateCollected.pins
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
                val currentInquiries = mapStateCollected.pinRemoveInquiries
                val updatedInquiries = currentInquiries + expiredInquiries
                viewModel.replaceInvalidPinIds(updatedInquiries)
            }
//            Toast.makeText(ctx, "INVALIDATOR $nowTimestamp", Toast.LENGTH_SHORT).show() //TODO: invalidator TOAST
        }
    }
    LaunchedEffect(mapStateCollected.pinRenderInquiries) { // RENDER!

        if (mapStateCollected.pinRenderInquiries.isNotEmpty()) {
            val pinRenderInquiriesSnapshot = mapStateCollected.pinRenderInquiries // SNAPSHOT OF THE STATE!
            osmdroidManager.pushManyPinsIntoPhysicalView(pinRenderInquiriesSnapshot) // RENDER!
            viewModel.subtractFromRenderInquiries(pinRenderInquiriesSnapshot) // SUBTRACT SNAPSHOT FROM CURRENT STATE!

            Toast.makeText(ctx, "LE RADIO -> BOTTOM", Toast.LENGTH_SHORT).show()
        }

    }
    LaunchedEffect(mapStateCollected.pinUpdateInquiries) { // RE-RENDER!

        if (mapStateCollected.pinUpdateInquiries.isNotEmpty()) {
            val pinUpdateInquiriesSnapshot = mapStateCollected.pinUpdateInquiries // SNAPSHOT OF THE STATE!
            osmdroidManager.updateManyPinsInsidePhysicalView(pinUpdateInquiriesSnapshot)// RE-RENDER!
            viewModel.subtractFromUpdateInquiries(pinUpdateInquiriesSnapshot) // SUBTRACT SNAPSHOT FROM CURRENT STATE!

            Toast.makeText(ctx, "LE RADIO -> BOTTOM UPDATE!!!", Toast.LENGTH_SHORT).show()
        }

    }
    LaunchedEffect(mapStateCollected.pinMoveInquiries) {

        if (mapStateCollected.pinMoveInquiries.isNotEmpty()) {
            // #0 snapshottim
            val pinMoveInquiriesSnapshot = mapStateCollected.pinMoveInquiries // SNAPSHOT OF THE STATE!
            val anInquiry = pinMoveInquiriesSnapshot.first()

            // #1 dispatch and update mvu
             when ( val result = viewModel.applyMapStateInquiry(anInquiry) ) {
                 is InquiryResult.Error -> {
                     Log.e("MOVEPIN", result.message)
                 }
                 is InquiryResult.PinPair -> {
                     Log.e("MOVEPIN", result.newPin.toString())
                     // #2 update radio + cold via repo
                     viewModel.repoPin.pushPinFurther(result.oldPin, result.newPin)
                 }
                 else -> Unit
             }

            // #3 subtract inquiries
            viewModel.removeInquiry(anInquiry)
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




    if (showDebug) {
        //DEBUG //
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier.statusBarsPadding()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Zoom: ${mapStateCollected.viewPort.mapZoom}", fontSize = 14.sp)
                Text("${mapStateCollected.viewPort.mapCenter}", fontSize = 14.sp)
                Text("${mapStateCollected.viewPort.mapBearing}", fontSize = 14.sp)
                Text("${mapStateCollected.pinRemoveInquiries}", fontSize = 15.sp)
                Text("${mapStateCollected.pinRenderInquiries}", fontSize = 15.sp)
                Text("${mapStateCollected.pins}", fontSize = 8.sp)
            }
        } // Box end
    }

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
                    },
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
                    },
                    onLoadHistory = { passedPinID ->
                        viewModel.repoPin.getAllVersions(passedPinID)
                    },
                    pinLogicalId = clickedPinLogicalId,
                )
            }
        }
    } // OLD ROUTER

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
//        isGranted ->
//        if (isGranted) {
//            startGpsRoutine()
//        } else {
//            Toast.makeText(ctx, "No permission -> no GPS!", Toast.LENGTH_SHORT).show()
//        }
    }

    // buttonchiki at the bottom
    Box(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(12.dp)
                .navigationBarsPadding()
                .imePadding(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val ROUNDED_PERCENT = 32
            val ICON_SIZE = 30

            val iconMod = Modifier
                .requiredSize((ICON_SIZE * 0.5).dp)

            Button(
                onClick = { uiStateManager.openPinsList() },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(ROUNDED_PERCENT)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Grading,
                    contentDescription = null,
                    modifier = iconMod
                )
            }
            Button(
                onClick = { uiStateManager.openChannelList() },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(ROUNDED_PERCENT)
            ) {
                Icon(
                    imageVector = Icons.Default.SatelliteAlt,
                    contentDescription = null,
                    modifier = iconMod
                )
            }
            Button(
                onClick = {

                    // #0
                    val hasPermission = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    // todo offload gps routine to a function to call it on permission granted in permission dialog launcher
                    if (hasPermission) {
                        if (uiStateCollected.isGpsActive) {
                            osmdroidManager.disableGpsReal()
                            uiStateManager.disableGpsUI()

                        } else {
                                                        // #1 UI state
                            uiStateManager.enableGpsUI()
                            uiStateManager.enableGpsInProgressUI()

                            // #2 poshla ebka
                            osmdroidManager.enableGpsAndCenterOn { success ->
                                uiStateManager.disableGpsInProgressUI()
                                if (!success) {
                                    Toast.makeText(ctx, "GPS isn't ready", Toast.LENGTH_SHORT).show()
                                    uiStateManager.disableGpsUI()
                                }

                            }
                        }
                    } else {
                        permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    }


                },
                enabled = !uiStateCollected.isGpsInSwitchingStage,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(ROUNDED_PERCENT)
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    tint = if (uiStateCollected.isGpsActive) Color.Green else LocalContentColor.current,
                    modifier = iconMod,
                    contentDescription = null,
                )
            }
            Button(
                onClick = {
                    // todo ТАК НЕЛЬЗЯ ПЕРЕДЕЛАТЬ НА ШИНЫ СОБЫТИЙ ДЛЯ ПЕРВОГО ЧТЕНИЯ
                    // todo ТАК НЕЛЬЗЯ ПЕРЕДЕЛАТЬ КНОПКУ НА Launched Effect по изменению СТЕЙТА
                    osmdroidManager.setViewport(mapStateCollected.viewPort.copy(mapBearing = 0.0f))
                    viewModel.updateViewPort(mapStateCollected.viewPort.copy(mapBearing = 0.0f))
                          },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(ROUNDED_PERCENT)
            ) {
                Icon(
                    imageVector = Icons.Default.ScreenRotationAlt,
                    contentDescription = null,
                    modifier = iconMod
                )
            }
            Button(
                onClick = { uiStateManager.openLayers() },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(ROUNDED_PERCENT)
            ) {
                Icon(
                    imageVector = Icons.Default.Layers,
                    modifier = iconMod,
                    contentDescription = null,
                    )
            }
            Button(
                onClick = { showDebug = !showDebug },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(ROUNDED_PERCENT)
            ) {
                Icon(
                    imageVector = if (showDebug) Icons.Default.DeveloperBoard else Icons.Default.DeveloperBoardOff,
                    modifier = iconMod,
                    contentDescription = null,
                )
            }
        } // buttons BOX finish
    }

    // NEW UI ROUTER
    ModalRenderer(
        uiStateManager,
        pins = mapStateCollected.pins,
        onPinRowClicked = { pin ->
            //todo НЕПРАВИЛЬНО, сперва нужна проверка на существование МАРКЕРА.... хотя зачем, по логике же перемещаемся.
            // TODO: СИЛЬНО ПОДУМАТЬ!!!
            osmdroidManager.getMapView().controller.animateTo(pin.pinPhysProps.geoPoint)
            viewModel.updateViewPort(mapStateCollected.viewPort.copy(mapCenter = pin.pinPhysProps.geoPoint))
        },
        onDownloadIntent = {

            when (uiStateCollected.isDownloading) {
                false -> {

                    val minZoom = mapStateCollected.viewPort.mapZoom.toInt() // FROM WHAT ZOOM LEVEL
                    val maxZoom = uiStateManager.uiStateR.value.cachingZoomSliderValue.toInt() // TO WAHT ZOOM LEVEL
                    val bounds = osmdroidManager.getMapView().boundingBox // CURRENT BOUNDS FROM PHYSICAL VIEW!

                    // #2 change UI
                    uiStateManager.startDownloadUI()

                    // #3 start task
                    osmdroidManager.startDownload(bounds, minZoom, maxZoom) { success ->
                        uiStateManager.stopDownloadUI()
                        Toast.makeText(ctx, if (success) "CACHE: saved!" else "CACHE: error!", Toast.LENGTH_SHORT).show()
                    }

                }

                true -> {

                    // #2 change UI
                    uiStateManager.stopDownloadUI()
                    // #3 stop all tasks
                    osmdroidManager.stopAllDownloads()

                }

            }



        }
    ) // modal renderer finish

} // ScreenMap finish


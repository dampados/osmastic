package com.example.osmastic

import android.content.Context
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.cachemanager.CacheManager
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.TilesOverlay
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

// ♻️🧭♻️🧭♻️🧭 MAP MANAGER!!! ♻️🧭♻️🧭♻️🧭
class OsmdroidManager(private val appContext: Context,                 // CLASS WRAPPER AROUND THE MapView !!!
                      private val onMapMovedCallback: (ViewPort) -> Unit, // SIMPLE CALLBACK! TO HERE WE PLACE LATER WHAT WILL UPDATE BOTH HOT + COLD!
                      private val onMapReadyViewPortCallback: suspend () -> ViewPort, // SIMPLE CALLBACK! we put cold state loading call!!! on the event afer which its safe
                      private val onMapReadyInitialPinsCallback: suspend () -> Set<PinLogical>,
                      private val onTapShortCallback: suspend (Context, GeoPoint) -> PinLogical, // SHORT TAPS REACTION CALLBACK <- a logical pin
                      private val onTapLongCallback: suspend (Context, GeoPoint) -> PinLogical, //  LONG TAPS
                      private val onPinClick: suspend (Context, Int) -> PinLogical?,             // Pin SHORT clicks!
                      private val onMarkerMovedCallback: (Int, GeoPoint) -> Unit,           // sync and unidirectional event spawn!
) {

    // TODO: GLOBAL - ADD SHARED COROUTINE SCOPE! THESE Dispatchers.Main - MONSTROUS
    private val mapView: MapView // OUTSOURCED MAPVIEW !!!
    private lateinit var myLocationOverlay: MyLocationNewOverlay

    init { // FACTORY ONE TIME INSTANTIATION
        mapView = MapView(appContext).apply {
            // 🚧🚧🚧 CONFIG 🚧🚧🚧

            Configuration.getInstance().setUserAgentValue("zalupa.zalupa.zalupa")

            // < сюда пихать кастомные тайлы.... >

//            val cartoVoyager = XYTileSource(
//                "CartoDBVoyager", 0, 19, 256, ".png",
//                arrayOf(
//                    "https://a.basemaps.cartocdn.com/rastertiles/voyager/",
//                    "https://b.basemaps.cartocdn.com/rastertiles/voyager/",
//                    "https://c.basemaps.cartocdn.com/rastertiles/voyager/",
//                    "https://d.basemaps.cartocdn.com/rastertiles/voyager/"
//                )
//            )
//            setTileSource(cartoVoyager)

            val voyagerBase = XYTileSource("VoyagerNoLabels", 0, 19, 256, ".png",
                arrayOf("https://a.basemaps.cartocdn.com/rastertiles/voyager_nolabels/",
                    "https://b.basemaps.cartocdn.com/rastertiles/voyager_nolabels/",
                    "https://c.basemaps.cartocdn.com/rastertiles/voyager_nolabels/",
                    "https://d.basemaps.cartocdn.com/rastertiles/voyager_nolabels/"))

            val labelsSource = XYTileSource("VoyagerLabels", 0, 19, 256, ".png",
                arrayOf("https://a.basemaps.cartocdn.com/rastertiles/voyager_only_labels/",
                    "https://b.basemaps.cartocdn.com/rastertiles/voyager_only_labels/",
                    "https://c.basemaps.cartocdn.com/rastertiles/voyager_only_labels/",
                    "https://d.basemaps.cartocdn.com/rastertiles/voyager_only_labels/"))

            setTileSource(voyagerBase)

            // Добавляем слой с надписями
            overlayManager.add(TilesOverlay(MapTileProviderBasic(appContext, labelsSource), appContext))

            invalidate()

//            setTileSource(TileSourceFactory.MAPNIK)

            setMultiTouchControls(true)
            setUseDataConnection(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)

            // 🍰🍰🍰 OVERLAYS SECTION 🍰🍰🍰
            val rotationOverlay = RotationGestureOverlay(this)
            rotationOverlay.isEnabled = true
            overlays.add(rotationOverlay)

            myLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(appContext), this).apply {
                disableMyLocation()
                disableFollowLocation()
                isDrawAccuracyEnabled = true
                overlays.add(this)
            }
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
                    onMapMovedCallback(newViewPort)
                    return false
                }
            })
            // CATCH TAPS -> VIEW -> MODEL (bottom -> top -> bottom)
            overlays.add(MapEventsOverlay(object : MapEventsReceiver {
                override fun singleTapConfirmedHelper(geoPoint: GeoPoint?): Boolean {
                    CoroutineScope(Dispatchers.Main).launch {
                        geoPoint?.let { geoPoint ->                                                       // "if not null, do something".
                            val newHotPin = onTapShortCallback(appContext, geoPoint)
                            pushOnePinIntoPhysicalView(newHotPin)
                        }
                    }
                    return true
                }
                override fun longPressHelper(geoPoint: GeoPoint?): Boolean {
                    CoroutineScope(Dispatchers.Main).launch {
                        geoPoint?.let { geoPoint ->                                                  // "if not null, do something".
                            val newHotPin = onTapLongCallback(appContext, geoPoint)
                            pushOnePinIntoPhysicalView(newHotPin)
                        }
                    }
                    return true
                }
            }))
            // ON MAP READY CATCH
            addOnFirstLayoutListener { _, _, _, _, _ ->
                CoroutineScope(Dispatchers.Main).launch {
                    val coldViewPort = onMapReadyViewPortCallback() // ACTUALLY READ -> business logic viewmodel -> come back here
                    setViewport(coldViewPort) // LOCAL ONLY
                    val coldPins = onMapReadyInitialPinsCallback()
                    pushManyPinsIntoPhysicalView(coldPins) // LOCAL ONLY
                }
            }
            // 🤙🤙🤙 CALLBACK SECTION 🤙🤙🤙
            // 🚧🚧🚧 CONFIG 🚧🚧🚧
        }
    }

    private fun constructMarkerFromLogicalPin(pin: PinLogical): LabeledMarker {
        val rotationDegrees = pin.pinPhysProps.rotationByte?.let { it * 360f / 127f }

        return LabeledMarker(
            mapView,
            pinLogicalId = pin.pinLogicalId,
            label = pin.pinPhysProps.label,
            rotation = rotationDegrees
        ).apply {
            position = pin.pinPhysProps.geoPoint
            textLabelBackgroundColor = 0x00000000
            textLabelFontSize = 72
            setTextIcon(pin.pinPhysProps.iconUnicode)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            setInfoWindow(null)
            setOnMarkerClickListener { _, _ ->
                CoroutineScope(Dispatchers.Main).launch {
                    onPinClick(appContext, pinLogicalId)?.let { pinLogical ->
                        updateOnePinInsidePhysicalView(pinLogical)
                    } // if null, DO NOTHING! rare case, states sync screwed up!
                        ?: run {
                            Toast.makeText(appContext, "AAAAAAAAAAAAAAAAA Pin not found in state", Toast.LENGTH_SHORT).show()
                        }

                }
                true
            }
            isDraggable = true
            setOnMarkerDragListener(object : Marker.OnMarkerDragListener {
                override fun onMarkerDragStart(marker: Marker) {}
                override fun onMarkerDrag(marker: Marker) {}
                override fun onMarkerDragEnd(marker: Marker) {
                    val typeCastedMarker = marker as LabeledMarker

                    val newGeoPoint = typeCastedMarker.position
                    val movedPinLogicalID = typeCastedMarker.pinLogicalId

                    onMarkerMovedCallback(movedPinLogicalID, newGeoPoint)
                }
            })
        }
    }

    // 🎊🎊🎊 FUN SECTION 🎊🎊🎊
    // TODO refactor: combine many and one to singel funcs to shrink redundancy!
    fun getMapView(): MapView = mapView        // 🪃🪃🪃  Factory calls this 🪃🪃🪃
    fun setViewport(incomingViewPort: ViewPort) {
        mapView.controller.animateTo(
            incomingViewPort.mapCenter,
            incomingViewPort.mapZoom,
            700,
            incomingViewPort.mapBearing,
//            0  // 0ms = instant
        )
    }
    fun pushOnePinIntoPhysicalView(incomingPin: PinLogical) {
        val marker = constructMarkerFromLogicalPin(incomingPin)
        mapView.overlays.add(marker)
        mapView.invalidate()
    }
    fun pushManyPinsIntoPhysicalView(incomingUpdatePins: Set<PinLogical>) {
        if (incomingUpdatePins.isEmpty()) return

        val markers = incomingUpdatePins.map { incomingPin -> constructMarkerFromLogicalPin(incomingPin) }
        mapView.overlays.addAll(markers)
        // No exception handling above NEEDED because:
        // - constructMarkerFromLogicalPin always returns a valid marker (no external resources)
        // - mapView.overlays.addAll only fails on null elements (we have none)
        // - mapView.invalidate() is a simple redraw request
        mapView.invalidate()
    }
    fun doGarbageCollect(invalidIds: Set<Int>): Set<Int> {
        val existing = mapView.overlays
            .filterIsInstance<LabeledMarker>()
            .map { it.pinLogicalId}
            .toSet()

        val canRemove = invalidIds.intersect(existing)
        val cannotRemove = invalidIds - existing

        canRemove.forEach { id ->
            val marker = mapView.overlays.find { it is LabeledMarker && it.pinLogicalId == id }
            mapView.overlays.remove(marker)
        }

        if (canRemove.isNotEmpty()) {
            mapView.invalidate()
        }

        // Return what we COULDNT remove (not yet born)
        return cannotRemove
    }

    fun updateOnePinInsidePhysicalView(incomingUpdatePin: PinLogical) {
        val idsToUpdate: Set<Int> = setOf(incomingUpdatePin.pinLogicalId)
        doGarbageCollect(idsToUpdate) // 100% bc we CLICK at the pin!
        pushOnePinIntoPhysicalView(incomingUpdatePin)
    }
    fun updateManyPinsInsidePhysicalView(incomingPins: Set<PinLogical>) {
        //#0 prep data
        val idsToUpdate: Set<Int> = incomingPins.mapTo(mutableSetOf()) { it.pinLogicalId }
        //#1 GC
        doGarbageCollect(idsToUpdate) // at this point WE ARE 100% SURE PINS WILL BE REMOVE IN ONE GO. bc all those were found in COLD.
        //#2 PUSH MANY
        pushManyPinsIntoPhysicalView(incomingPins)
    }

    //deprecated
    fun centerOnMyLocation(onResult: (Boolean, GeoPoint?) -> Unit) {
        if (!myLocationOverlay.isMyLocationEnabled) {
            myLocationOverlay.enableMyLocation()
        }

        // Таймаут 10 секунд
        val timeoutJob = CoroutineScope(Dispatchers.Main).launch {
            delay(10000)
            if (myLocationOverlay.myLocation == null) {
                myLocationOverlay.disableMyLocation()
                onResult(false, null)
            }
        }

        myLocationOverlay.runOnFirstFix {
            timeoutJob.cancel()
            val geoPoint = myLocationOverlay.myLocation
            if (geoPoint != null) {
                CoroutineScope(Dispatchers.Main).launch {
                    mapView.controller.animateTo(geoPoint)
                }
//                mapView.controller.animateTo(geoPoint)
                onResult(true, geoPoint)
            } else {
                onResult(false, null)
            }
            // TODO решить ВЫКЛЮЧАТЬ ИЛИ НЕТ
//            myLocationOverlay.disableMyLocation()
            myLocationOverlay.disableFollowLocation()
        }
    }

    fun enableGpsAndCenterOn(onTriedPositioning: (Boolean) -> Unit) {
        if (!myLocationOverlay.isMyLocationEnabled) {
            myLocationOverlay.enableMyLocation()
        }
        myLocationOverlay.runOnFirstFix {
            val location = myLocationOverlay.myLocation
            CoroutineScope(Dispatchers.Main).launch {
                if (location != null) {
                    mapView.controller.animateTo(location)
                    onTriedPositioning(true)
                } else {
                    onTriedPositioning(false)
                }
            }
        }
    }

    fun disableGpsReal() {
        myLocationOverlay.disableMyLocation()
    }


//    private var currentTask: CacheManager.CacheManagerTask? = null
//
//    fun startDownload(
//        bounds: BoundingBox,
//        minZoom: Int,
//        maxZoom: Int,
//        onComplete: (Boolean) -> Unit
//    ) {
//        // Отменяем предыдущую задачу, если была
//        stopDownload()
//
//        val cacheManager = CacheManager(mapView)
//        currentTask = cacheManager.downloadAreaAsync(
//            appContext,
//            bounds,
//            minZoom,
//            maxZoom,
//            object : CacheManager.CacheManagerCallback {
//                override fun onTaskComplete() {
//                    currentTask = null
//                    onComplete(true)
//                }
//                override fun onTaskFailed(errors: Int) {
//                    currentTask = null
//                    onComplete(false)
//                }
//                override fun updateProgress(progress: Int, currentZoom: Int, zoomMin: Int, zoomMax: Int) {}
//                override fun downloadStarted() {}
//                override fun setPossibleTilesInArea(total: Int) {}
//            }
//        )
//    }
//
//    fun stopDownload() {
//        currentTask?.cancel(true)  // ← отменяем задачу
//        currentTask = null
//    }

    private var currentCacheManager: CacheManager? = null

    fun startDownload(
        bounds: BoundingBox,
        minZoom: Int,
        maxZoom: Int,
        onComplete: (Boolean) -> Unit
    ) {
        stopAllDownloads() // CANCEL EVERYTHING

        val cacheManager = CacheManager(mapView)
        currentCacheManager = cacheManager

//        cacheManager.downloadAreaAsync(
        cacheManager.downloadAreaAsyncNoUI(
            appContext, bounds, minZoom, maxZoom,
            object : CacheManager.CacheManagerCallback {
                override fun onTaskComplete() {
                    currentCacheManager = null
                    onComplete(true)
                }
                override fun onTaskFailed(errors: Int) {
                    currentCacheManager = null
                    onComplete(false)
                }
                override fun updateProgress(progress: Int, currentZoom: Int, zoomMin: Int, zoomMax: Int) {}
                override fun downloadStarted() {}
                override fun setPossibleTilesInArea(total: Int) {}
            }
        )
    }

    fun stopAllDownloads() {
        currentCacheManager?.cancelAllJobs()
        currentCacheManager = null
    }


    // 🎊🎊🎊 FUN SECTION 🎊🎊🎊
}
// ♻️🧭♻️🧭♻️🧭 MAP MANAGER!!! ♻️🧭♻️🧭♻️🧭
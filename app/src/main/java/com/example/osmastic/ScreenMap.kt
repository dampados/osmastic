package com.example.osmastic

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
//new ones:
import android.content.Context
import android.view.MotionEvent
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.views.MapView

@Composable
fun ScreenMap(modifier: Modifier = Modifier) {
    val appViewModel: StateGlobalViewModel = viewModel() // POINTER TO STATE
    val uiState by appViewModel.uiState.collectAsState() // POINTER TO STATE

    AndroidView(
//        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK )
                setMultiTouchControls(true)
                controller.setCenter(uiState.mapCenter) // STATE READING
                controller.setZoom(uiState.mapZoom)     // STATE READING

                // UPDATING STATE LOC + ZOOM HERE
                setOnTouchListener { v, event ->
                    if (event.action == MotionEvent.ACTION_UP) {
                        // Finger lifted - save position
                        appViewModel.updateMapPosition(
                            center = org.osmdroid.util.GeoPoint(
                                projection.currentCenter.latitude,
                                projection.currentCenter.longitude
                            ),
                            zoom = zoomLevelDouble
                        )
                    }
                    false // Let MapView handle the touch normally
                }


            }
        },
        update = { mapView ->
            // When global state changes → update map
            mapView.controller.setCenter(uiState.mapCenter)
            mapView.controller.setZoom(uiState.mapZoom)
        }
    )


    Box(contentAlignment = Alignment.Center, modifier = modifier.statusBarsPadding()) {


        Text(uiState.mapZoom.toString(), fontSize = 14.sp)
        Text(uiState.mapCenter.toString(), fontSize = 14.sp, modifier = modifier.statusBarsPadding())

    }
}
package com.example.osmastic

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
//new ones:
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.views.MapView

@Composable
fun ScreenMap(modifier: Modifier = Modifier) {
    Box(contentAlignment = Alignment.Center) {
        Text("MAP SCREEN", fontSize = 100.sp)
    }
}
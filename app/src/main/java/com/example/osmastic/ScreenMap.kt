package com.example.osmastic

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp

@Composable
fun ScreenMap(modifier: Modifier = Modifier) {
    Box(contentAlignment = Alignment.Center) {
        Text("MAP SCREEN", fontSize = 100.sp)
    }
}
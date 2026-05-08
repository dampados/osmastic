package com.example.osmastic.modal

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.osmastic.StateUIViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayersDialog(
    uiManager: StateUIViewModel,
    onDownloadIntent: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = { uiManager.closeAnyModal() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // #1
//            Text("Layers", fontSize = 20.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)

            // #2 Layers
            Row(verticalAlignment = Alignment.CenterVertically) {
//                Text("Layer", modifier = Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = true,
                        onClick = { /* TODO Voyager switch */ },
                        label = { Text("Voyager") }
                    )
                    FilterChip(
                        selected = false,
                        onClick = { /* TODO MAPNIK switch */ },
                        label = { Text("Default") },
                        enabled = false
                    )
                    FilterChip(
                        selected = false,
                        onClick = { /* TODO Satellite switch */ },
                        label = { Text("Satellite ") },
                        enabled = false
                    )
                }
            }

            // #3 dark/light theme enforcer
            OutlinedTextField(
                value = "System",
                onValueChange = { /* todo uh, theme change callback? */ },
                readOnly = false,
                label = { Text("Theme") },
                trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
                modifier = Modifier.fillMaxWidth(),
                enabled = false
            )

            // #4 caching
            val uiStateCollected by uiManager.uiStateR.collectAsState()

//            var cachingZoomSliderValue by remember { mutableFloatStateOf(12f) }
//            var isDownloading by remember { mutableStateOf(false) }

            Column {
                Slider(
                    value = uiStateCollected.cachingZoomSliderValue,
                    onValueChange = { uiManager.setCacheZoom(it) },
                    valueRange = 10f..18f,
                    steps = 8,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiStateCollected.isDownloading
                )
//                Button(
//                    onClick = { /* TODO: start downloading */ },
//                    modifier = Modifier.fillMaxWidth(),
//                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
//                ) {
//                    Text("Cache ${iuCacheZoom.toInt()} zoom deep")
//                }

                val buttonColor = if (uiStateCollected.isDownloading)
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.95f)
                else
                    MaterialTheme.colorScheme.primary

                Button(
                    onClick = {
//                        isDownloading = !isDownloading
//                        uiManager.toggleDownloadUI()
                        /* TODO: start downloading */
                        onDownloadIntent()
                              },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = buttonColor)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Текст слева
                        Text(
                            if (uiStateCollected.isDownloading) "In progress..."
                            else "Cache ${uiStateCollected.cachingZoomSliderValue.toInt()} zoom deep"
                        )

//                        val animatedAlpha by animateFloatAsState(
//                            targetValue = if (uiStateCollected.isDownloading) 0.1f else 1f,
//                            animationSpec = infiniteRepeatable(
//                                animation = tween(200),
//                                repeatMode = RepeatMode.Reverse
//                            )
//                        )
                        val infiniteTransition = rememberInfiniteTransition()
                        val animatedAlpha by infiniteTransition.animateFloat(
                            initialValue = 0.1f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(200),
                                repeatMode = RepeatMode.Reverse
                            )
                        )
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(
                                    color = if (uiStateCollected.isDownloading) Color.White.copy(alpha = animatedAlpha)
                                    else Color.DarkGray,
                                    shape = CircleShape
                                )
                        )
                    }
                }
            }

            // #5 drop cache
            Button(
                onClick = { /* TODO: drop cache */ },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Text("Drop tiles cache")
            }
        }
    }
}
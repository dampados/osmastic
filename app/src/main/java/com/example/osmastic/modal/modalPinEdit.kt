package com.example.osmastic.modal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.osmastic.PinLogical
import com.example.osmastic.PinUI
import org.osmdroid.util.GeoPoint

@Composable
fun PinEditDialog(
    geoPoint: GeoPoint? = null,
    existingPinUI: PinUI? = null,
    onConfirm: (PinUI) -> Unit,
    onDismiss: () -> Unit,
    onLoadHistory: (suspend (Int) -> Set<PinLogical>)? = null,
    pinLogicalId: Int? = null,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp)
                .padding(8.dp),
            shape = RoundedCornerShape(8.dp)
        ) {


            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                val pinFromPhysical = when {
                    existingPinUI != null -> existingPinUI
                    geoPoint != null -> PinUI(geoPoint = geoPoint)
                    else -> error("PinEditDialog: need either geoPoint or existingPinUI")
                }

                var selectedTab by remember { mutableStateOf(0) }
                var pinUnderConstruction by remember { mutableStateOf(pinFromPhysical) }

                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("👀") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("📝") }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("🔃") }
                    )
                    Tab(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        text = { Text("🙈") }
                    )
                    if (existingPinUI == null) {  // I FORBID to edit TTL - TTL is absolute and final
                        Tab(
                            selected = selectedTab == 4,
                            onClick = { selectedTab = 4 },
                            text = { Text("⏲️") })
                    } else {
                        Tab(selected = selectedTab == 5,
                            onClick = { selectedTab = 5 },
                            text = { Text("⏪") })
                    }
                }

                when (selectedTab) {
//                    0 -> {
//                        Text("Position update is not yet supported (｡•́︿•̀｡)")
//                    }
                    0 -> {
                        OutlinedTextField(
                            value = pinUnderConstruction.iconUnicode,
                            onValueChange = {
                                pinUnderConstruction = pinUnderConstruction.copy(iconUnicode = it)
                            },
                            label = { Text("Emoji") }
                        )
                    }
                    1 -> {
                        OutlinedTextField(
                            value = pinUnderConstruction.label,
                            onValueChange = {
                                pinUnderConstruction = pinUnderConstruction.copy(label = it)
                            },
                            label = { Text("Label") }
                        )
                    }
                    2 -> {
                        val rotationSteps = 127  // we fitting single byte for protobuf! 0-126 maps to 0-360
                        val rotationInDegs = ((pinUnderConstruction.rotationByte ?: 0) * (360f / rotationSteps)).toInt()
                        Text("Rotation: ${rotationInDegs}°")
                        Slider(
                            value = rotationInDegs.toFloat(),
                            onValueChange = { degrees ->
                                val byteValue = (degrees * (rotationSteps / 360f)).toInt().coerceIn(0, rotationSteps - 1)
                                pinUnderConstruction = pinUnderConstruction.copy(
                                    rotationByte = byteValue
                                )
                            },
                            valueRange = 0f..360f,
                            steps = rotationSteps - 1
                        )
                    }
                    3 -> {
                        Switch(
                            checked = pinUnderConstruction.isHiddenBeforeTTL,
                            onCheckedChange = {
                                pinUnderConstruction = pinUnderConstruction.copy(isHiddenBeforeTTL = it)
                            }
                        )
                    }
                    4 -> {
                        val maxMinutes = 16383
                        var ttl by remember { mutableStateOf(pinUnderConstruction.minutesTTL.coerceIn(0, maxMinutes)) }

                        val daysRaw = ttl / 1440
                        val hoursRaw = (ttl % 1440) / 60
                        val minutesRaw = ttl % 60

                        fun updateTtl(newTtl: Int) {
                            ttl = newTtl.coerceIn(0, maxMinutes)
                            pinUnderConstruction = pinUnderConstruction.copy(minutesTTL = ttl)
                        }

                        Column {
                            Text("TTL: ${if (ttl == 0) "Eternal" else "${daysRaw}d ${hoursRaw}h ${minutesRaw}m"}")

                            Text("Days: $daysRaw")
                            Slider(
                                value = daysRaw.toFloat(),
                                onValueChange = { days ->
                                    if (days.toInt() == 11) {
                                        // Max days = reset hours/minutes to 0
                                        updateTtl(11 * 1440)
                                    } else {
                                        val newTtl = (days.toInt() * 1440) + (ttl % 1440)
                                        updateTtl(newTtl)
                                    }
                                },
                                valueRange = 0f..11f
                            )

                            // Disabled when days == 11
                            Text("Hours: $hoursRaw")
                            Slider(
                                value = hoursRaw.toFloat(),
                                onValueChange = { hours ->
                                    val newTtl = (ttl / 1440 * 1440) + (hours.toInt() * 60) + (ttl % 60)
                                    updateTtl(newTtl)
                                },
                                valueRange = 0f..23f,
                                enabled = daysRaw != 11
                            )

                            // Disabled when days == 11
                            Text("Minutes: $minutesRaw")
                            Slider(
                                value = minutesRaw.toFloat(),
                                onValueChange = { mins ->
                                    val newTtl = (ttl / 60 * 60) + mins.toInt()
                                    updateTtl(newTtl)
                                },
                                valueRange = 0f..59f,
                                enabled = daysRaw != 11
                            )
                        }
                    }
                    5 -> {

                        var versions by remember { mutableStateOf<Set<PinLogical>>(emptySet()) }
                        var isLoading by remember { mutableStateOf(true) }

                        LaunchedEffect(Unit) {
                            val result = onLoadHistory?.invoke(pinLogicalId!!) ?: emptySet() // todo господь пощади, не бей за это!
                            versions = result
                            isLoading = false
                        }

                        if (isLoading) {
                            Text("Загрузка истории...")
                        } else if (versions.isEmpty()) {
                            Text("История пуста")
                        } else {
                            LazyColumn {
                                items(versions.toList()) { version ->
                                    HistoryVersionCard(version = version)
                                    HorizontalDivider(
                                        Modifier,
                                        DividerDefaults.Thickness,
                                        DividerDefaults.color
                                    ) // если нужен разделитель
                                }
                            }
                        }
                    }


                }// finisher

                Button(onClick = {
                    onConfirm(pinUnderConstruction)
                }) {
                    Text("Good")
                }
            } // COLUMN FINISH
        } // SURFACE FINISH
    } // DIALOG (MODAL) FINISH
}


@Composable
private fun HistoryVersionCard(
    version: PinLogical,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = version.pinPhysProps.iconUnicode,
            fontSize = 28.sp
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = version.pinPhysProps.label.ifEmpty { "<no label>" },
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "🔄 Epoch: ${version.lamportEpoch} | ✏️ ${version.editorMark.take(4)}",
                fontSize = 12.sp
            )
            Text(
                text = "📍 ${version.pinPhysProps.geoPoint.latitude}, ${version.pinPhysProps.geoPoint.longitude}",
                fontSize = 12.sp
            )
            if (version.pinPhysProps.rotationByte != null) {
                Text(
                    text = "🔄 Rot: ${version.pinPhysProps.rotationByte}",
                    fontSize = 12.sp
                )
            }
//            Text(
//                text = "⏳ TTL: ${formatTTL(version.expirationTimestamp)}",
//                fontSize = 12.sp
//            )
            Text(
                text = "😶‍🌫️ Hidden: ${if (version.pinPhysProps.isHiddenBeforeTTL) "Yes" else "No"}",
                fontSize = 12.sp
            )
        }
    }
}



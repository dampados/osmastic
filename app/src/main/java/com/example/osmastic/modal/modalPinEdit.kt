package com.example.osmastic.modal

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.osmastic.PinUI
import org.osmdroid.util.GeoPoint

@Composable
fun PinEditDialog(
    geoPoint: GeoPoint? = null,
    existingPinUI: PinUI? = null,
    onConfirm: (PinUI) -> Unit,
    onDismiss: () -> Unit
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

//                val pinFromPhysical = if (existingPinUI != null) else PinUI(geoPoint = geoPoint!!)
//                val pinFromPhysical = PinUI(geoPoint = geoPoint)
//                val pinFromPhysical = existingPinUI ?: PinUI(geoPoint = geoPoint!!)

                val pinFromPhysical = when {
                    existingPinUI != null -> existingPinUI
                    geoPoint != null -> PinUI(geoPoint = geoPoint)
                    else -> error("PinEditDialog: need either geoPoint or existingPinUI")
                }

                var selectedTab by remember { mutableStateOf(0) }
                var pinUnderConstruction by remember { mutableStateOf(pinFromPhysical) }

                TabRow(selectedTabIndex = selectedTab) {
//                    Tab(
//                        selected = selectedTab == 0,
//                        onClick = { selectedTab = 0 },
//                        text = { Text("🧭") }
//                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("👀") }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("📝") }
                    )
                    Tab(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        text = { Text("🔃") }
                    )
                    Tab(
                        selected = selectedTab == 4,
                        onClick = { selectedTab = 4 },
                        text = { Text("🙈") }
                    )
                    if (existingPinUI == null) {  // I FORBID to edit TTL - TTL is absolute and final
                        Tab(
                            selected = selectedTab == 5,
                            onClick = { selectedTab = 5 },
                            text = { Text("⏲️") })
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
//                    3 -> {
//                        val rotationInDegs = (pinUnderConstruction.rotationByte ?: 0) * (360f/255f)
//                        Text("Rotation: ${rotationInDegs.toInt()}°")
//                        Slider(
//                            value = rotationInDegs,
//                            onValueChange = { degrees ->
//                                val byteValue = (degrees * (255f/360f)).toInt()  // 360 → 255
//                                pinUnderConstruction = pinUnderConstruction.copy(
//                                    rotationByte = byteValue
//                                )
//                            },
//                            valueRange = 0f..360f,
//                            steps = 359
//                        )
//                    }
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
//                    5 -> {
//                        val maxMinutes = 16383
//                        var remainingRawTime = pinUnderConstruction.minutesTTL
//
//                        val days = remainingRawTime / 1440
//                        val hours = (remainingRawTime % 1440) / 60
//                        val remainingMinutes = remainingRawTime % 60
//
//                        Text("TTL: ${if (days > 0) "${days}d " else ""}${if (hours > 0) "${hours}h " else ""}${remainingMinutes}m")
//
//                        Slider(
//                            value = remainingRawTime.toFloat(),
//                            onValueChange = { newMinutes ->
//                                pinUnderConstruction = pinUnderConstruction.copy(minutesTTL = newMinutes.toInt().coerceIn(0, maxMinutes))
//                            },
//                            valueRange = 0f..maxMinutes.toFloat()
//                        )
//                    }


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
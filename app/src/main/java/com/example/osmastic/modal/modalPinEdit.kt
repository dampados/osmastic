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
import kotlin.text.ifEmpty

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
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("🧭") }
                    )
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
                    0 -> {
                        Text("Position update is not yet supported (｡•́︿•̀｡)")
                    }
                    1 -> {
                        OutlinedTextField(
                            value = pinUnderConstruction.iconUnicode,
                            onValueChange = {
                                pinUnderConstruction = pinUnderConstruction.copy(iconUnicode = it)
                            },
                            label = { Text("Emoji") }
                        )
                    }
                    2 -> {
                        OutlinedTextField(
                            value = pinUnderConstruction.label ?: "",
                            onValueChange = {
                                pinUnderConstruction = pinUnderConstruction.copy(label = it.ifEmpty { null })
                            },
                            label = { Text("Label") }
                        )
                    }
                    3 -> {
                        val rotationInDegs = (pinUnderConstruction.rotationByte ?: 0) * (360f/255f)
                        Text("Rotation: ${rotationInDegs.toInt()}°")
                        Slider(
                            value = rotationInDegs,
                            onValueChange = { degrees ->
                                val byteValue = (degrees * (255f/360f)).toInt()  // 360 → 255
                                pinUnderConstruction = pinUnderConstruction.copy(
                                    rotationByte = byteValue
                                )
                            },
                            valueRange = 0f..360f,
                            steps = 359
                        )
                    }
                    4 -> {
                        Switch(
                            checked = pinUnderConstruction.isHiddenBeforeTTL,
                            onCheckedChange = {
                                pinUnderConstruction = pinUnderConstruction.copy(isHiddenBeforeTTL = it)
                            }
                        )
                    }
                    5 -> {
                        var uiHoursTTL = pinUnderConstruction.hoursTTL
//                            Text("TTL: $uiHoursTTL hours")
                        Text("TTL: $uiHoursTTL minutes! testing") //TODO replace on hours!
                        Slider(
                            value = uiHoursTTL.toFloat(),
                            onValueChange = { sliderValue ->
                                pinUnderConstruction = pinUnderConstruction.copy(hoursTTL = sliderValue.toInt())
                            },
                            valueRange = 0f..255f,
                            steps = 253
                        )
                    }
                }

                Button(onClick = {
                    onConfirm(pinUnderConstruction)
                }) {
                    Text("Good")
                }
            } // COLUMN FINISH
        } // SURFACE FINISH
    } // DIALOG (MODAL) FINISH
}
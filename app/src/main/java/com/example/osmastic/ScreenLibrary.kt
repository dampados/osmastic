package com.example.osmastic

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
// TEST needed:
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.produceState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.random.Random
import com.example.osmastic.db.Pin
//another coroutine shit instead of lifecycleScope
import androidx.compose.runtime.rememberCoroutineScope

@Composable
fun ScreenLibraryOld(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text("PIN LIST SCREEN", fontSize = 20.sp)
    }
}

@Composable
fun ScreenLibrary(modifier: Modifier = Modifier) {
    val viewModel: StateGlobalViewModel = viewModel()
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // CREATE button
        Button(
            onClick = {
                scope.launch(Dispatchers.IO) {
//                    val testPin = Pin(
//                        id = Random.nextInt(1000, 9999),
////                        lamportEpoch = 0,
////                        editorHash = byteArrayOf(1, 2, 3, 4, 5),
////                        latitude = 5555555,
////                        longitude = 6666666,
////                        iconType = 0x1F4CD,
////                        label = "Test Pin ${System.currentTimeMillis() % 1000}"
////                    )
//                    viewModel.pinDao.insertAndReturnId(testPin)
                }
            }
        ) {
            Text("Create Test Pin")
        }

        Spacer(modifier = Modifier.height(8.dp))

        // DELETE ALL button
//        Button(
//            onClick = {
//                scope.launch(Dispatchers.IO) {
//                    val allPins = viewModel.pinDao.getAll()
//                    allPins.forEach { pin ->
//                        viewModel.pinDao.delete(pin)
//                    }
//                }
//            }
//        ) {
//            Text("Delete All Pins")
//        }

//        Spacer(modifier = Modifier.height(16.dp))

        // Display pins (simpler version)
//        val pins by produceState<List<Pin>>(initialValue = emptyList()) {
//            scope.launch(Dispatchers.IO) {
//                while (true) {
//                    value = viewModel.pinDao.getAll()
//                    delay(1000)
//                }
//            }
//        }

//        Text("Total Pins: ${pins.size}", fontSize = 18.sp)
//
//        LazyColumn {
//            items(pins) { pin ->
//                Text(
//                    text = "📍 ${pin.label ?: "Unnamed"}",
//                    modifier = Modifier.padding(4.dp)
//                )
//            }
//        }
    }
}
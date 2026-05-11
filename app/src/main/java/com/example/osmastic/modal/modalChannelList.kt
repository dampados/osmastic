package com.example.osmastic.modal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.osmastic.StateUIViewModel

@OptIn(ExperimentalMaterial3Api::class)
//@Preview(showBackground = true)
@Composable
fun ChannelListDialog(
    manager: StateUIViewModel,
) {
    ModalBottomSheet(
        onDismissRequest = { manager.closeAnyModal() },
//        modifier = Modifier.heightIn(max = 400.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "(ノ｀△´)ノ Channel selection is not yet implemented!",
                fontSize = 18.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
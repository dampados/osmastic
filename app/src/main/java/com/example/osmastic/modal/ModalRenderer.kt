package com.example.osmastic.modal

import ChannelListDialog
import PinListDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.osmastic.ModalType
import com.example.osmastic.PinLogical
import com.example.osmastic.StateUIViewModel

@Composable
fun ModalRenderer(
    manager: StateUIViewModel,
    pins: Set<PinLogical>,
    onPinRowClicked: (PinLogical) -> Unit,
) {
    val uiState by manager.uiStateR.collectAsState()

    when (uiState.activeModal) {
        ModalType.PinsList -> PinListDialog(manager, pins, onPinRowClicked)
        ModalType.ChannelsList -> ChannelListDialog(manager)
        null -> Unit
    }
}
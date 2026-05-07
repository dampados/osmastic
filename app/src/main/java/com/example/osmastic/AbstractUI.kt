package com.example.osmastic

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

sealed class ModalType {
    object PinsList : ModalType()
    object ChannelsList : ModalType()
}

data class StateUIModel(
    val activeModal: ModalType? = null,
    val isGpsActive: Boolean = false,
    val isGpsInProgress: Boolean = false,
)

class StateUIViewModel {
    private val _uiStateRW = MutableStateFlow(StateUIModel())
    val uiStateR: StateFlow<StateUIModel> = _uiStateRW.asStateFlow()


    //--- public funcs ---//

    fun enableGpsUI() {
        _uiStateRW.update { it.copy(isGpsActive = true) }
    }

    fun disableGpsUI() {
        _uiStateRW.update { it.copy(isGpsActive = false) }
    }

    fun enableGpsInProgressUI() {
        _uiStateRW.update { it.copy(isGpsInProgress = true) }
    }

    fun disableGpsInProgressUI() {
        _uiStateRW.update { it.copy(isGpsInProgress = false) }
    }


    fun openPinsList() {
        _uiStateRW.update { it.copy(activeModal = ModalType.PinsList) }
    }

    fun openChannelList() {
        _uiStateRW.update { it.copy(activeModal = ModalType.ChannelsList) }
    }

    fun closeAnyModal() {
        _uiStateRW.update { it.copy(activeModal = null) }
    }
    //--- public funcs ---//



}


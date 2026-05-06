package com.example.osmastic

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

sealed class ModalType {
    object PinsList : ModalType()
}

data class StateUIModel(
    val activeModal: ModalType? = null
)

class StateUIViewModel {
    private val _uiStateRW = MutableStateFlow(StateUIModel())
    val uiStateR: StateFlow<StateUIModel> = _uiStateRW.asStateFlow()




    //--- public funcs ---//
    fun openPinsList() {
        _uiStateRW.update { it.copy(activeModal = ModalType.PinsList) }
    }

    fun closeAnyModal() {
        _uiStateRW.update { it.copy(activeModal = null) }
    }
    //--- public funcs ---//



}


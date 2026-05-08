package com.example.osmastic

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

sealed class ModalType {
    object PinsList : ModalType()
    object ChannelsList : ModalType()
    object Layers : ModalType()
}

data class StateUIModel(
    val activeModal: ModalType? = null,
    val isGpsActive: Boolean = false,
    val isGpsInSwitchingStage: Boolean = false,

    val cachingZoomSliderValue: Float = 12f,
    val isDownloading: Boolean = false,
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
        _uiStateRW.update { it.copy(isGpsInSwitchingStage = true) }
    }

    fun disableGpsInProgressUI() {
        _uiStateRW.update { it.copy(isGpsInSwitchingStage = false) }
    }

    fun setCacheZoom(zoom: Float) {
        _uiStateRW.update { it.copy(cachingZoomSliderValue = zoom) }
    }

    fun toggleDownloadUI() {
//        _uiStateRW.update { it.copy(isDownloading = !uiStateR.value.isDownloading) } //ebat ya degenerat
        _uiStateRW.update { it.copy(isDownloading = !it.isDownloading) }
    }

    fun startDownloadUI() {
        _uiStateRW.update { it.copy(isDownloading = true) }
    }

    fun stopDownloadUI() {
        _uiStateRW.update { it.copy(isDownloading = false) }
    }



    fun openPinsList() {
        _uiStateRW.update { it.copy(activeModal = ModalType.PinsList) }
    }

    fun openChannelList() {
        _uiStateRW.update { it.copy(activeModal = ModalType.ChannelsList) }
    }

    fun openLayers() {
        _uiStateRW.update { it.copy(activeModal = ModalType.Layers)}
    }

    fun closeAnyModal() {
        _uiStateRW.update { it.copy(activeModal = null) }
    }
    //--- public funcs ---//



}


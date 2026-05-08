
package com.mockloc.base

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

abstract class BaseViewModel : ViewModel() {

    private val _uiEvents = Channel&lt;UIEvent&gt;()
    val uiEvents = _uiEvents.receiveAsFlow()

    protected fun sendEvent(event: UIEvent) = viewModelScope.launch {
        _uiEvents.send(event)
    }

    sealed interface UIEvent {
        data class ShowToast(val message: String) : UIEvent
        data class Navigate(val destination: String) : UIEvent
        data object ShowLoading : UIEvent
        data object HideLoading : UIEvent
    }
}

abstract class BaseViewModelWithState&lt;S&gt;(
    initialState: S
) : BaseViewModel() {

    private val _uiState = MutableStateFlow(initialState)
    val uiState: StateFlow&lt;S&gt; = _uiState.asStateFlow()

    protected fun setState(newState: S.() -&gt; S) {
        _uiState.value = _uiState.value.newState()
    }

    protected fun updateState(block: (S) -&gt; S) {
        _uiState.value = block(_uiState.value)
    }
}


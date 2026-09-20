package com.tabslify.core.ui

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow

class SharedViewModel : ViewModel() {
    private val _uiEvent = MutableSharedFlow<Boolean>()
    val uiEvent = _uiEvent.asSharedFlow()

    fun fireEvent(value: Boolean) {
        _uiEvent.tryEmit(value)
    }

    private val _pendingEmailOpen = MutableStateFlow<Pair<String, String>?>(null)
    val pendingEmailOpen = _pendingEmailOpen

    fun setPendingEmailOpen(value: Pair<String, String>?) {
        _pendingEmailOpen.value = value
    }

    private val _pendingAiSession = MutableStateFlow<String?>(null)
    val pendingAiSession = _pendingAiSession

    fun setPendingAiSession(value: String?) {
        _pendingAiSession.value = value
    }
}

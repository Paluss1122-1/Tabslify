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

    private val _appliedEmailOpen = MutableStateFlow<Pair<String, String>?>(null)
    val appliedEmailOpen = _appliedEmailOpen

    fun setPendingEmailOpen(value: Pair<String, String>?) {
        _appliedEmailOpen.value = null
        _pendingEmailOpen.value = value
    }

    fun markEmailOpenApplied(value: Pair<String, String>) {
        _appliedEmailOpen.value = value
    }

    fun clearPendingEmailOpen() {
        _appliedEmailOpen.value = null
        _pendingEmailOpen.value = null
    }

    private val _pendingAiSession = MutableStateFlow<String?>(null)
    val pendingAiSession = _pendingAiSession

    fun setPendingAiSession(value: String?) {
        _pendingAiSession.value = value
    }
}

package com.werner.black_overlay // ★★★ DEIN PAKETNAME ★★★

import android.util.Log
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ViewModel responsible for managing the state of the overlay visibility.
 */
class OverlayViewModel : ViewModel() {

    private val TAG = "OverlayViewModel"

    // Private MutableStateFlow to hold the current state
    private val _isOverlayVisible = MutableStateFlow(false)
    // Public immutable StateFlow exposed to observers
    val isOverlayVisible: StateFlow<Boolean> = _isOverlayVisible.asStateFlow()

    /**
     * Sets the overlay state to visible, if it's not already.
     */
    fun showOverlay() {
        if (!_isOverlayVisible.value) {
            Log.d(TAG, "Setting overlay state to VISIBLE")
            _isOverlayVisible.value = true
        } else {
             Log.d(TAG, "showOverlay called but overlay is already visible.")
        }
    }

    /**
     * Sets the overlay state to hidden, if it's not already.
     */
    fun hideOverlay() {
        if (_isOverlayVisible.value) {
            Log.d(TAG, "Setting overlay state to HIDDEN")
            _isOverlayVisible.value = false
        } else {
            Log.d(TAG, "hideOverlay called but overlay is already hidden.")
        }
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "ViewModel cleared")
    }
}
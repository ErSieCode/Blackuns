package com.werner.black_overlay // ★★★ DEIN PAKETNAME ★★★

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch

// Timeout for detecting a double tap (in milliseconds)
private const val DOUBLE_TAP_TIMEOUT_MS = 250L // Increased timeout for easier double-tapping

/**
 * A Composable function that displays a full-screen black overlay.
 * It intercepts all touch events and detects double taps to trigger dismissal.
 *
 * @param onDismissRequest Lambda function to be invoked when a double tap is detected.
 */
@Composable
fun BlackscreenOverlayView(onDismissRequest: () -> Unit) {
    val TAG = "BlackscreenOverlayView"
    val scope = rememberCoroutineScope()
    var lastTapTimeMillis by remember { mutableStateOf(0L) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black) // Set background to solid black
            // --- Touch Input Handling ---
            .pointerInput(Unit) { // Use pointerInput for detailed gesture detection
                detectTapGestures(
                    // Consume the press event immediately to block underlying views
                    onPress = {
                        Log.v(TAG, "onPress detected")
                        try {
                            awaitRelease() // Wait until the finger is lifted
                            Log.v(TAG, "awaitRelease finished")
                        } catch (e: Exception){
                             Log.w(TAG, "Pointer input cancelled during awaitRelease", e)
                        }
                    },
                    onTap = { // Called after a completed tap (down and up)
                        val currentTime = System.currentTimeMillis()
                        Log.d(TAG, "onTap detected, currentTime: $currentTime")
                        if (currentTime - lastTapTimeMillis < DOUBLE_TAP_TIMEOUT_MS) {
                            // Double tap detected
                            Log.i(TAG, "Double tap detected! Requesting dismiss.")
                            // Launch the dismiss request in a coroutine scope
                            scope.launch { onDismissRequest() }
                            lastTapTimeMillis = 0L // Reset timer after double tap
                        } else {
                            // First tap (or tap after timeout)
                            Log.d(TAG, "First tap detected.")
                            lastTapTimeMillis = currentTime
                        }
                    }
                    // We don't need onDoubleTap, onLongPress here as we handle double tap manually
                )
            }
            // --- End Touch Input Handling ---
    ) {
        // The Box itself is the overlay. No child content needed.
    }
}
package com.werner.black_overlay // ★★★ DEIN PAKETNAME ★★★

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * A LifecycleService that manages the overlay view and the persistent notification.
 */
class OverlayService : LifecycleService() {

    private val TAG = "OverlayService-BO" // Unique Tag for Logging
    private lateinit var windowManager: WindowManager
    private var overlayView: ComposeView? = null
    private lateinit var viewModel: OverlayViewModel
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob()) // Scope tied to service lifecycle
    private lateinit var notificationManager: NotificationManagerCompat

    // Provides a ViewModelStoreOwner scoped to this service instance
    private class ServiceViewModelStoreOwner : ViewModelStoreOwner {
        override val viewModelStore = ViewModelStore()
        fun clear() { viewModelStore.clear() }
    }
    private val serviceViewModelStoreOwner = ServiceViewModelStoreOwner()

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate: Service instance created.")
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        notificationManager = NotificationManagerCompat.from(this)
        // Initialize ViewModel using the service-scoped owner
        viewModel = ViewModelProvider(serviceViewModelStoreOwner)[OverlayViewModel::class.java]

        createNotificationChannel()
        // Start as a foreground service immediately
        startForeground(Constants.NOTIFICATION_ID, createNotification())
        Log.i(TAG, "Service started in foreground.")

        // Observe the overlay visibility state from the ViewModel
        serviceScope.launch {
            viewModel.isOverlayVisible.collectLatest { isVisible ->
                Log.d(TAG, "Observed overlay visibility state: $isVisible")
                if (isVisible) {
                    showOverlayView()
                } else {
                    hideOverlayView()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId) // Important for LifecycleService
        val action = intent?.action
        Log.d(TAG, "onStartCommand Received: Action=$action, Flags=$flags, StartId=$startId")

        when (action) {
            Constants.ACTION_SHOW_OVERLAY -> {
                // Check permission *before* trying to show
                if (Settings.canDrawOverlays(this)) {
                    Log.i(TAG, "Action: Show Overlay requested.")
                    viewModel.showOverlay() // Trigger state change
                } else {
                    Log.e(TAG, "Action: Show Overlay failed - Permission 'SYSTEM_ALERT_WINDOW' missing!")
                    // Optional: Notify user via a toast (can't if background) or stop service
                    stopSelf() // Stop service if essential permission is missing
                }
            }
            Constants.ACTION_STOP_SERVICE -> {
                Log.i(TAG, "Action: Stop Service requested.")
                viewModel.hideOverlay() // Ensure overlay is hidden via state change first
                stopSelf() // Stop the service itself
            }
            else -> {
                 Log.w(TAG, "Received unknown or null action: $action")
            }
        }
        // If the service is killed, try to restart it
        return START_STICKY
    }

    @SuppressLint("ClickableViewAccessibility") // We handle touch via Composables.kt
    private fun showOverlayView() {
        // Prevent adding multiple overlays or adding without permission
        if (overlayView != null) {
             Log.d(TAG, "showOverlayView: Overlay already exists.")
             return
        }
        if (!Settings.canDrawOverlays(this)) {
             Log.e(TAG, "showOverlayView: Cannot show - Permission missing!")
             // Optionally trigger MainActivity again or notify user through other means if possible
             stopSelf()
             return
        }

        Log.i(TAG, "showOverlayView: Creating and adding the overlay view...")

        // Configure WindowManager LayoutParams for the overlay
        val overlayLayoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, // Fill width
            WindowManager.LayoutParams.MATCH_PARENT, // Fill height
            // Type determines the window layer (important for drawing over other apps)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_SYSTEM_ALERT, // Fallback for older APIs

            // Flags: Crucial for behavior (Overlay on top, non-interactive except for our gesture)
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or    // Doesn't receive keyboard focus
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or // Layout within the entire screen (incl. sys bars)
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or // Allow drawing outside screen bounds (might help cover everything)
            WindowManager.LayoutParams.FLAG_FULLSCREEN or       // Try to cover status bar etc.
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,    // Events outside are passed to windows below (usually not needed with MATCH_PARENT)

            PixelFormat.TRANSLUCENT // Use transparency format
        ).apply {
            gravity = Gravity.TOP or Gravity.START // Position at top-left
            x = 0
            y = 0
            // Note: FLAG_HARDWARE_ACCELERATED is usually enabled by default for Compose
        }

        // Create the ComposeView to host our Composable
        overlayView = ComposeView(this).apply {
            // Dispose the composition when the view is detached from the window
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            // Set the content to our BlackscreenOverlayView Composable
            setContent {
                BlackscreenOverlayView(onDismissRequest = {
                    Log.d(TAG, "Overlay dismiss requested via double tap.")
                    viewModel.hideOverlay() // Trigger state change to hide
                 })
            }
        }

        // Add the view to the WindowManager
        try {
            // Check if service is still running before adding
            if (lifecycle.currentState != Lifecycle.State.DESTROYED) { // Use lifecycle check
                 windowManager.addView(overlayView, overlayLayoutParams)
                 Log.i(TAG, "Overlay view added to WindowManager.")
            } else {
                 Log.w(TAG, "Service was destroyed before view could be added.")
                 overlayView = null // Clean up ref
            }
        } catch (e: Exception) {
            Log.e(TAG, "FATAL: Failed to add overlay view to WindowManager!", e)
            overlayView = null // Clean up reference on failure
            // Consider stopping the service if adding the view fails critically
            // stopSelf()
        }
    }

    private fun hideOverlayView() {
        if (overlayView == null) {
             // Log.v(TAG, "hideOverlayView: Overlay already null or not shown.");
             return
        }
        Log.i(TAG, "hideOverlayView: Attempting to remove overlay view...")
        // Capture the view to remove in a local variable for safety within the lambda/try-catch
        val viewToRemove = overlayView
        overlayView = null // Clear the main reference immediately

        viewToRemove?.let {
            try {
                // Check if the view is actually attached before trying to remove
                if (it.isAttachedToWindow) {
                    windowManager.removeView(it)
                    Log.i(TAG, "Overlay view successfully removed from WindowManager.")
                } else {
                    Log.w(TAG, "hideOverlayView: View was already detached or not attached.")
                }
            } catch (e: IllegalArgumentException) {
                // This can happen if the view was already removed or WindowManager state is inconsistent
                Log.w(TAG, "hideOverlayView: Failed to remove view (already removed?): ${e.message}")
            } catch (e: Exception) {
                // Catch other potential exceptions during removal
                Log.e(TAG, "Error removing overlay view from WindowManager!", e)
            }
        }
    }

    private fun createNotificationChannel() {
        // Channel creation is only needed for Android O (API 26) and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
             Log.d(TAG, "Creating/Updating Notification Channel: ${Constants.NOTIFICATION_CHANNEL_ID}")
            val channelName = getString(R.string.notification_channel_name)
            val channelDescription = getString(R.string.notification_channel_description)
            // Low importance: No sound, doesn't peek, appears in shade but possibly minimized
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(Constants.NOTIFICATION_CHANNEL_ID, channelName, importance).apply {
                description = channelDescription
                setSound(null, null) // Explicitly disable sound
                enableVibration(false) // Explicitly disable vibration
                setShowBadge(false) // Don't show a badge dot
            }
            // Get NotificationManager system service
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            try {
                manager.createNotificationChannel(channel)
                 Log.i(TAG, "Notification channel registration successful.")
            } catch(e: Exception) {
                 Log.e(TAG, "Failed to create notification channel!", e)
            }
        } else {
             Log.d(TAG, "Notification channels not required for this API level.")
        }
    }

    @SuppressLint("MissingPermission", "LaunchActivityFromNotification") // Permission checks happen before startForeground/show
    private fun createNotification(): Notification {
        Log.d(TAG, "Building notification...")

        // --- PendingIntents for Notification Actions ---
        // Intent to trigger showing the overlay
        val showOverlayIntent = Intent(this, OverlayService::class.java).setAction(Constants.ACTION_SHOW_OVERLAY)
        // Use FLAG_IMMUTABLE for security, FLAG_UPDATE_CURRENT to update if needed
        val showOverlayPendingIntent = PendingIntent.getService(this, 301, showOverlayIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        // Intent to trigger stopping the service
        val stopServiceIntent = Intent(this, OverlayService::class.java).setAction(Constants.ACTION_STOP_SERVICE)
        // Use a different request code for the stop action
        val stopServicePendingIntent = PendingIntent.getService(this, 302, stopServiceIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE) // Use FLAG_CANCEL_CURRENT if single stop needed? Update current seems fine.
        // -------------------------------------------

        // Build the notification
        val builder = NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title)) // Title shown in the notification
            .setContentText(getString(R.string.notification_text))   // Text shown below the title
            .setSmallIcon(R.drawable.ic_eye_white) // ★★★ MANDATORY: Small icon shown in status bar ★★★
            // Optional: Large icon shown in the notification shade (can be null)
            // .setLargeIcon(BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher))
            .setPriority(NotificationCompat.PRIORITY_LOW) // Low priority - less intrusive
            .setOngoing(true) // Makes the notification non-dismissible by swiping
            .setSilent(true) // No sound or vibration when the notification itself appears/updates
            .setVisibility(NotificationCompat.VISIBILITY_SECRET) // Hide sensitive content on lock screen
            // Action Button 1: Show Overlay
            .addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.ic_eye_white, // Icon for the action button
                    getString(R.string.action_show_overlay), // Text for the action button
                    showOverlayPendingIntent // PendingIntent triggered by the button
                ).build() // Build the action
            )
            // Action Button 2: Stop Service
            .addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.ic_power_off_red, // Icon for the action button
                    getString(R.string.action_stop_service), // Text for the action button
                    stopServicePendingIntent // PendingIntent triggered by the button
                ).build() // Build the action
            )
            // Apply MediaStyle to potentially keep actions visible in compact mode
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                 .setShowActionsInCompactView(0, 1) // Try to show action 0 and 1 in compact view
                 // Optional: Link to media session token if controlling media
                 // .setMediaSession(mediaSessionToken)
             )
            // Optional: Set a category (e.g., SERVICE, ALARM, etc.)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            // Optional: Set a timeout after which the notification is cancelled (NOT for ongoing)
            // .setTimeoutAfter(millis)
            // Optional: Group notifications if you have multiple
            // .setGroup(GROUP_KEY)

        Log.d(TAG, "Notification built.")
        return builder.build()
    }

    override fun onDestroy() {
        super.onDestroy() // ★★★ Call superclass implementation FIRST ★★★
        Log.i(TAG, "onDestroy: Service is being destroyed.")
        // 1. Cancel Coroutines to stop background work in the service scope
        serviceScope.cancel("Service is being destroyed")
        // 2. Ensure the overlay is removed
        hideOverlayView()
        // 3. Clean up ViewModel
        serviceViewModelStoreOwner.clear()
        // 4. Stop foreground state and remove notification (use flag for removal)
        stopForeground(STOP_FOREGROUND_REMOVE) // Recommended way to remove notification on stopForeground
        // 5. Explicitly cancel notification just in case stopForeground didn't catch it
        notificationManager.cancel(Constants.NOTIFICATION_ID)
        Log.i(TAG, "Service destroyed, resources cleaned up.")
    }

    /** Service binding is not used for started services like this one. */
    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent) // Important for LifecycleService internal handling
        Log.d(TAG, "onBind called, returning null.")
        return null // We don't provide binding
    }
}
package com.werner.black_overlay // ★★★ DEIN PAKETNAME ★★★

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

/**
 * The main entry point Activity.
 * Its sole purpose is to check and request necessary permissions
 * and then start the [OverlayService]. It has no visible UI itself.
 */
class MainActivity : ComponentActivity() {

    private val TAG = "MainActivity-BO" // Unique Tag for Logging

    // --- Activity Result Launchers for Permission Requests ---

    // Launcher for the SYSTEM_ALERT_WINDOW (Overlay) permission request
    private lateinit var overlayPermissionLauncher: ActivityResultLauncher<Intent>

    // Launcher for the POST_NOTIFICATIONS permission request (Android 13+)
    private lateinit var notificationPermissionLauncher: ActivityResultLauncher<String>

    // --------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate: Initializing permission launchers.")

        // Initialize the launchers in onCreate
        initializePermissionLaunchers()

        // No need to set content view as this activity is translucent and finishes quickly

        // Start the permission checking flow
        checkAndRequestPermissions()
    }

    /** Initializes the ActivityResultLaunchers */
    private fun initializePermissionLaunchers() {
        overlayPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ -> // We don't need the result data ('it'), just need to re-check
                // This callback is invoked AFTER returning from the system settings screen
                Log.d(TAG, "Overlay permission result received (returned from Settings).")
                // Re-check if the permission was granted *now*
                if (Settings.canDrawOverlays(this)) {
                    Log.i(TAG, "Overlay permission GRANTED after returning from settings.")
                    // Permission granted, proceed to start the service
                    startOverlayServiceAndFinish()
                } else {
                    // Permission still denied
                    Log.w(TAG, "Overlay permission DENIED after returning from settings.")
                    Toast.makeText(this, getString(R.string.permission_denied_toast), Toast.LENGTH_LONG).show()
                    // Exit the app if the crucial permission is denied
                    finishAndRemoveTask()
                }
            }

        notificationPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
                // This callback is invoked AFTER the user responds to the notification permission dialog
                Log.d(TAG, "Notification permission result received: isGranted = $isGranted")
                if (!isGranted) {
                    // Permission denied, inform the user but continue the flow
                    // The app can technically work without notifications, but it's crippled
                    Log.w(TAG, "Notification permission DENIED by user.")
                    Toast.makeText(this, getString(R.string.permission_notification_denied_toast), Toast.LENGTH_LONG).show()
                } else {
                    Log.i(TAG, "Notification permission GRANTED by user.")
                }
                // Crucially, proceed to the *next* permission check (overlay) regardless of grant status
                checkOverlayPermission()
            }
    }

    /**
     * Starts the permission checking sequence.
     * Begins with Notification permission on Android 13+, then Overlay permission.
     */
    private fun checkAndRequestPermissions() {
        Log.d(TAG, "Starting permission check flow...")
        // --- Step 1: Check/Request Notification Permission (Android 13+) ---
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                // Check if permission is already granted
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED -> {
                    Log.i(TAG, "Notification permission already granted.")
                    // Permission granted, proceed directly to overlay check
                    checkOverlayPermission()
                }
                // Optional: Check if we should show a rationale (user previously denied without 'never ask again')
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    Log.d(TAG, "Rationale for notification permission should be shown (optional). Requesting anyway.")
                    // Explain why you need it here (e.g., using a Dialog) then request...
                    // For simplicity, we just request directly:
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    // The launcher callback will handle the result and continue the flow
                }
                // Permission not granted, and no rationale needed (first time or 'never ask again')
                else -> {
                    Log.d(TAG, "Requesting notification permission.")
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    // The launcher callback will handle the result and continue the flow
                }
            }
        } else {
            // Notification permission not required before Android 13
            Log.d(TAG, "Notification permission not required for this Android version.")
            // Proceed directly to overlay check
            checkOverlayPermission()
        }
    }

    /** Checks and requests the SYSTEM_ALERT_WINDOW (Overlay) permission. */
    private fun checkOverlayPermission() {
        Log.d(TAG, "Checking overlay (SYSTEM_ALERT_WINDOW) permission...")
        // --- Step 2: Check/Request Overlay Permission (All relevant versions) ---
        if (!Settings.canDrawOverlays(this)) {
            // Permission is missing, need to guide user to system settings
            Log.w(TAG, "Overlay permission MISSING. Creating intent to request.")
            Toast.makeText(this, getString(R.string.permission_overlay_request_toast), Toast.LENGTH_LONG).show()

            // Create an Intent to open the specific settings page for this app
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName") // Uri pointing to this app's package
            )

            // Try to launch the settings activity using the launcher
            try {
                Log.d(TAG, "Launching overlay settings intent...")
                overlayPermissionLauncher.launch(intent)
                // DO NOT finish the activity here - wait for the launcher's callback when the user returns
            } catch (e: ActivityNotFoundException) {
                // Handle the rare case where the settings activity doesn't exist
                Log.e(TAG, "Could not launch overlay settings activity!", e)
                Toast.makeText(this, "Fehler: Overlay-Einstellungen konnten nicht geöffnet werden.", Toast.LENGTH_LONG).show()
                finishAndRemoveTask() // Exit if settings cannot be opened
            }
        } else {
            // Permission is already granted
            Log.i(TAG, "Overlay permission already granted.")
            // Proceed to start the service
            startOverlayServiceAndFinish()
        }
    }

    /** Starts the OverlayService in the foreground and finishes this activity. */
    private fun startOverlayServiceAndFinish() {
        Log.i(TAG, "All required permissions granted (or handled). Starting OverlayService...")
        val serviceIntent = Intent(this, OverlayService::class.java)

        try {
            // Use startForegroundService for services that will call startForeground()
            ContextCompat.startForegroundService(this, serviceIntent)
            Log.d(TAG, "ContextCompat.startForegroundService called.")
        } catch (e: SecurityException) {
            // Might happen on Android 14+ if FOREGROUND_SERVICE_SPECIAL_USE is missing,
            // or on Android 12+ if POST_NOTIFICATIONS is missing when needed for startForeground.
            Log.e(TAG, "SecurityException while starting foreground service!", e)
            Toast.makeText(this, "Fehler: Service konnte wegen fehlender Berechtigungen nicht gestartet werden.", Toast.LENGTH_LONG).show()
        } catch (e: IllegalStateException) {
            // Can happen on Android 12+ if trying to start from background without meeting exceptions.
            // Should be less likely here as we start from a foreground Activity.
            Log.e(TAG, "IllegalStateException while starting foreground service (App in background?)!", e)
            Toast.makeText(this, "Fehler: Service konnte nicht gestartet werden (App im Hintergrund?).", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            // Catch any other unexpected errors during service start
            Log.e(TAG, "Generic Exception while starting foreground service!", e)
            Toast.makeText(this, "Unbekannter Fehler beim Starten des Service.", Toast.LENGTH_LONG).show()
        } finally {
            // CRUCIAL: Finish this transparent activity regardless of service start success/failure
            // This ensures the user doesn't see an empty/transparent screen.
            Log.d(TAG, "Finishing MainActivity via finishAndRemoveTask().")
            finishAndRemoveTask() // Close activity and remove from recents
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy: MainActivity instance destroyed.")
    }
}
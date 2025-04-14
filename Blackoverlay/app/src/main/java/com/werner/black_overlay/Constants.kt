package com.werner.black_overlay // ★★★ DEIN PAKETNAME ★★★

/**
 * Object holding constant values used throughout the application.
 */
object Constants {
    // Notification Channel configuration
    const val NOTIFICATION_CHANNEL_ID = "blackscreen_overlay_channel_werner_01" // Unique ID for the channel
    const val NOTIFICATION_ID = 198411 // Unique ID for the notification itself

    // Intent Actions for communication with the service
    const val ACTION_SHOW_OVERLAY = "com.werner.black_overlay.ACTION_SHOW_OVERLAY"
    const val ACTION_STOP_SERVICE = "com.werner.black_overlay.ACTION_STOP_SERVICE"
}
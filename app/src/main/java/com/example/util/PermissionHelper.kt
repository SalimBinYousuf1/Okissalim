package com.example.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat

object PermissionHelper {
    private const val TAG = "PermissionHelper"

    /**
     * Checks if the camera permission is granted for scanning pairing QR codes.
     */
    fun isCameraPermissionGranted(context: Context): Boolean {
        return CrashProtector.safeRun(TAG, false) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Creates an intent to navigate the user to this app's system settings page.
     */
    fun createAppSettingsIntent(context: Context): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    /**
     * Safely launch an intent with fallback.
     */
    fun launchIntentSafely(context: Context, intent: Intent): Boolean {
        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch intent: ${e.message}", e)
            false
        }
    }
}

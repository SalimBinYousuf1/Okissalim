package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.model.PairingPayload

class ControllerPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var pairedPayload: PairingPayload?
        get() {
            val json = prefs.getString(KEY_PAIRED_PAYLOAD, null) ?: return null
            return PairingPayload.fromJson(json)
        }
        set(value) {
            prefs.edit().apply {
                if (value != null) {
                    putString(KEY_PAIRED_PAYLOAD, value.toJson())
                    putBoolean(KEY_IS_PAIRED, true)
                } else {
                    remove(KEY_PAIRED_PAYLOAD)
                    putBoolean(KEY_IS_PAIRED, false)
                }
                apply()
            }
        }

    val isPaired: Boolean
        get() = prefs.getBoolean(KEY_IS_PAIRED, false) && pairedPayload != null

    var lastConnectedTimestamp: Long
        get() = prefs.getLong(KEY_LAST_CONNECTED, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_CONNECTED, value).apply()

    fun clearPairing() {
        prefs.edit()
            .remove(KEY_PAIRED_PAYLOAD)
            .putBoolean(KEY_IS_PAIRED, false)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "salim_controller_preferences"
        private const val KEY_PAIRED_PAYLOAD = "paired_host_payload"
        private const val KEY_IS_PAIRED = "is_host_paired"
        private const val KEY_LAST_CONNECTED = "last_connected_timestamp"

        @Volatile
        private var instance: ControllerPreferences? = null

        fun getInstance(context: Context): ControllerPreferences {
            return instance ?: synchronized(this) {
                instance ?: ControllerPreferences(context.applicationContext).also { instance = it }
            }
        }
    }
}

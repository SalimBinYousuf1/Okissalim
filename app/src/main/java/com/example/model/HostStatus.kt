package com.example.model

import org.json.JSONObject

enum class ConnectionState {
    STANDBY,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    WAITING_ON_HOST,
    HOST_UNREACHABLE;

    val displayLabel: String
        get() = when (this) {
            STANDBY -> "Disconnected"
            CONNECTING -> "Connecting to Ubaid..."
            CONNECTED -> "Connected & Streaming"
            RECONNECTING -> "Reconnecting (ICE)..."
            WAITING_ON_HOST -> "Waiting on Ubaid"
            HOST_UNREACHABLE -> "Ubaid Unreachable"
        }
}

data class BatteryInfo(
    val levelPercent: Int = 100,
    val isCharging: Boolean = false,
    val temperatureCelsius: Float = 25f
)

data class NetworkInfo(
    val type: String = "WiFi",
    val isConnected: Boolean = true,
    val details: String = "Connected"
)

data class ScreenInfo(
    val isScreenOn: Boolean = true,
    val width: Int = 1080,
    val height: Int = 2400,
    val densityDpi: Int = 420
)

data class TelemetryPayload(
    val batteryPercent: Int = 100,
    val isCharging: Boolean = false,
    val networkType: String = "WiFi",
    val networkConnected: Boolean = true,
    val isScreenOn: Boolean = true,
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        fun fromJson(jsonStr: String): TelemetryPayload? {
            return try {
                val obj = JSONObject(jsonStr)
                if (obj.optString("type") != "status_telemetry") return null
                TelemetryPayload(
                    batteryPercent = obj.optInt("batteryPercent", 100),
                    isCharging = obj.optBoolean("isCharging", false),
                    networkType = obj.optString("networkType", "WiFi"),
                    networkConnected = obj.optBoolean("networkConnected", true),
                    isScreenOn = obj.optBoolean("isScreenOn", true),
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

sealed class FirebaseConfigStatus {
    data class Configured(val projectId: String) : FirebaseConfigStatus()
    data class Missing(val reason: String) : FirebaseConfigStatus()
}

package com.example.model

import org.json.JSONObject

data class PairingPayload(
    val pairingId: String,
    val hostName: String,
    val projectId: String,
    val apiKey: String,
    val storageBucket: String,
    val screenWidth: Int,
    val screenHeight: Int,
    val densityDpi: Int,
    val createdAt: Long = System.currentTimeMillis(),
    val version: Int = 1
) {
    fun toJson(): String {
        val obj = JSONObject()
        obj.put("pairingId", pairingId)
        obj.put("hostName", hostName)
        obj.put("projectId", projectId)
        obj.put("apiKey", apiKey)
        obj.put("storageBucket", storageBucket)
        obj.put("screenWidth", screenWidth)
        obj.put("screenHeight", screenHeight)
        obj.put("densityDpi", densityDpi)
        obj.put("createdAt", createdAt)
        obj.put("version", version)
        return obj.toString(2)
    }

    companion object {
        fun fromJson(jsonStr: String): PairingPayload? {
            return try {
                val obj = JSONObject(jsonStr)
                val id = obj.optString("pairingId", "")
                if (id.isBlank()) return null
                PairingPayload(
                    pairingId = id,
                    hostName = obj.optString("hostName", "Ubaid Host"),
                    projectId = obj.optString("projectId", "salim-x-ubaid"),
                    apiKey = obj.optString("apiKey", "AIzaSyBdiTj7YRtZ5ncYv6few_Gfaw9h-mbqU3w"),
                    storageBucket = obj.optString("storageBucket", "salim-x-ubaid.firebasestorage.app"),
                    screenWidth = obj.optInt("screenWidth", 1080),
                    screenHeight = obj.optInt("screenHeight", 2400),
                    densityDpi = obj.optInt("densityDpi", 420),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                    version = obj.optInt("version", 1)
                )
            } catch (e: Exception) {
                null
            }
        }

        fun parse(rawInput: String): PairingPayload? {
            val trimmed = rawInput.trim()
            if (trimmed.isBlank()) return null
            if (trimmed.startsWith("{")) {
                return fromJson(trimmed)
            }
            // Accept manual code entry (e.g. UBAID-XXXXXX or alphanumeric ID)
            if (trimmed.length >= 4) {
                val cleanId = if (trimmed.startsWith("UBAID-", ignoreCase = true)) trimmed.uppercase() else "UBAID-${trimmed.uppercase()}"
                return PairingPayload(
                    pairingId = cleanId,
                    hostName = "Ubaid Host",
                    projectId = "salim-x-ubaid",
                    apiKey = "AIzaSyBdiTj7YRtZ5ncYv6few_Gfaw9h-mbqU3w",
                    storageBucket = "salim-x-ubaid.firebasestorage.app",
                    screenWidth = 1080,
                    screenHeight = 2400,
                    densityDpi = 420
                )
            }
            return null
        }
    }
}

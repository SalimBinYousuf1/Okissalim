package com.example.model

import org.json.JSONObject

data class NotificationPayload(
    val key: String,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val postTime: Long,
    val isRemoved: Boolean = false
) {
    fun toJson(): String {
        val obj = JSONObject()
        obj.put("type", if (isRemoved) "notification_removed" else "notification_posted")
        obj.put("key", key)
        obj.put("packageName", packageName)
        obj.put("appName", appName)
        obj.put("title", title)
        obj.put("text", text)
        obj.put("postTime", postTime)
        return obj.toString()
    }

    companion object {
        fun fromJson(jsonStr: String): NotificationPayload? {
            return try {
                val obj = JSONObject(jsonStr)
                val type = obj.optString("type")
                if (!type.startsWith("notification_")) return null
                NotificationPayload(
                    key = obj.optString("key", ""),
                    packageName = obj.optString("packageName", ""),
                    appName = obj.optString("appName", "App"),
                    title = obj.optString("title", ""),
                    text = obj.optString("text", ""),
                    postTime = obj.optLong("postTime", System.currentTimeMillis()),
                    isRemoved = (type == "notification_removed")
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

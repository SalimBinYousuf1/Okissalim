package com.example.model

import org.json.JSONArray
import org.json.JSONObject

data class FileItem(
    val name: String,
    val absolutePath: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModifiedMs: Long
) {
    val formattedSize: String
        get() {
            if (isDirectory) return "--"
            if (sizeBytes < 1024) return "$sizeBytes B"
            val kb = sizeBytes / 1024.0
            if (kb < 1024) return String.format("%.1f KB", kb)
            val mb = kb / 1024.0
            if (mb < 1024) return String.format("%.1f MB", mb)
            val gb = mb / 1024.0
            return String.format("%.2f GB", gb)
        }

    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("name", name)
        obj.put("path", absolutePath)
        obj.put("isDirectory", isDirectory)
        obj.put("sizeBytes", sizeBytes)
        obj.put("lastModifiedMs", lastModifiedMs)
        return obj
    }

    companion object {
        fun fromJson(obj: JSONObject): FileItem {
            return FileItem(
                name = obj.optString("name", "Unknown"),
                absolutePath = obj.optString("path", ""),
                isDirectory = obj.optBoolean("isDirectory", false),
                sizeBytes = obj.optLong("sizeBytes", 0L),
                lastModifiedMs = obj.optLong("lastModifiedMs", 0L)
            )
        }

        fun listFromJson(jsonStr: String): Pair<String, List<FileItem>>? {
            return try {
                val root = JSONObject(jsonStr)
                if (root.optString("type") != "file_list_response") return null
                val path = root.optString("path", "/")
                val arr = root.optJSONArray("items") ?: JSONArray()
                val list = mutableListOf<FileItem>()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i)
                    if (obj != null) {
                        list.add(fromJson(obj))
                    }
                }
                Pair(path, list)
            } catch (e: Exception) {
                null
            }
        }

        fun listToJson(path: String, items: List<FileItem>): String {
            val root = JSONObject()
            root.put("type", "file_list_response")
            root.put("path", path)
            val arr = JSONArray()
            items.forEach { arr.put(it.toJson()) }
            root.put("items", arr)
            return root.toString()
        }
    }
}

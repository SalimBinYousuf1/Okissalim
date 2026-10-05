package com.example.util

import android.os.Looper
import android.util.Log

object CrashProtector {
    const val TAG = "SalimCrashProtector"
    var lastError: String? = null

    fun install() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val errorMsg = "Uncaught exception on thread ${thread.name}: ${throwable.message}\n${Log.getStackTraceString(throwable)}"
            Log.e(TAG, errorMsg, throwable)
            lastError = throwable.message ?: "Unexpected error"

            val isMainThread = (Looper.myLooper() == Looper.getMainLooper()) || thread.name == "main"
            val isRecoverableBackgroundThread = thread.name.contains("Camera", ignoreCase = true) ||
                    thread.name.contains("webrtc", ignoreCase = true) ||
                    thread.name.contains("SurfaceViewRenderer", ignoreCase = true) ||
                    thread.name.contains("DefaultDispatcher", ignoreCase = true) ||
                    thread.name.contains("EGL", ignoreCase = true) ||
                    thread.name.contains("OkHttp", ignoreCase = true) ||
                    throwable is SecurityException ||
                    throwable is IllegalStateException

            // Prevent app autoclose on background threads or transient camera/WebRTC exceptions
            if (!isMainThread || isRecoverableBackgroundThread) {
                Log.w(TAG, "Gracefully suppressed uncaught background exception on thread '${thread.name}' to keep Salim alive: ${throwable.message}")
                return@setDefaultUncaughtExceptionHandler
            }

            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    fun <T> safeRun(tag: String = TAG, fallback: T, block: () -> T): T {
        return try {
            block()
        } catch (e: Throwable) {
            Log.e(tag, "Caught exception in safeRun: ${e.message}", e)
            lastError = e.message
            fallback
        }
    }
}

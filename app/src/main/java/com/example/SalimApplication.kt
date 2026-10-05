package com.example

import android.app.Application
import android.util.Log
import com.example.util.CrashProtector
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

class SalimApplication : Application() {
    private val TAG = "SalimApplication"

    override fun onCreate() {
        super.onCreate()
        // 1. Crash protector is installed at the earliest possible lifecycle moment
        CrashProtector.install()
        Log.d(TAG, "SalimApplication launched with active CrashProtector.")

        // 2. Safe Firebase initialization with project fallback
        CrashProtector.safeRun(TAG, Unit) {
            try {
                if (FirebaseApp.getApps(this).isEmpty()) {
                    try {
                        FirebaseApp.initializeApp(this)
                        Log.d(TAG, "Firebase initialized via default options.")
                    } catch (e: Exception) {
                        Log.w(TAG, "Default Firebase initialization failed, applying manual fallback: ${e.message}")
                        val fallbackOptions = FirebaseOptions.Builder()
                            .setApplicationId("1:428293373821:android:764070a9c93f569c629614")
                            .setApiKey("AIzaSyBdiTj7YRtZ5ncYv6few_Gfaw9h-mbqU3w")
                            .setProjectId("salim-x-ubaid")
                            .setStorageBucket("salim-x-ubaid.firebasestorage.app")
                            .build()
                        FirebaseApp.initializeApp(this, fallbackOptions)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Firebase initialization error caught safely: ${e.message}", e)
            }
        }
    }
}

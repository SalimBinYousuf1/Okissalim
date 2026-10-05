package com.example.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ControllerPreferences
import com.example.data.ControllerSignalingManager
import com.example.data.ControllerWebRtcManager
import com.example.model.ConnectionState
import com.example.model.FileItem
import com.example.model.FirebaseConfigStatus
import com.example.model.NotificationPayload
import com.example.model.PairingPayload
import com.example.model.TelemetryPayload
import com.example.util.CrashProtector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.webrtc.VideoTrack

class ControllerViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "ControllerViewModel"
    private val prefs = ControllerPreferences.getInstance(application)
    val signalingManager = ControllerSignalingManager(application)
    val webRtcManager = ControllerWebRtcManager(application, signalingManager)

    private val _isPaired = MutableStateFlow(prefs.isPaired)
    val isPaired: StateFlow<Boolean> = _isPaired.asStateFlow()

    private val _pairedPayload = MutableStateFlow(prefs.pairedPayload)
    val pairedPayload: StateFlow<PairingPayload?> = _pairedPayload.asStateFlow()

    val connectionState: StateFlow<ConnectionState> = webRtcManager.connectionState
    val firebaseStatus: StateFlow<FirebaseConfigStatus> = signalingManager.firebaseStatus
    val remoteVideoTrack: StateFlow<VideoTrack?> = webRtcManager.remoteVideoTrack
    val telemetry: StateFlow<TelemetryPayload> = webRtcManager.telemetry
    val notifications: StateFlow<List<NotificationPayload>> = webRtcManager.notifications
    val currentPath: StateFlow<String> = webRtcManager.currentPath
    val fileItems: StateFlow<List<FileItem>> = webRtcManager.fileItems
    val isLoadingFiles: StateFlow<Boolean> = webRtcManager.isLoadingFiles
    val fileTransferStatus: StateFlow<String?> = webRtcManager.fileTransferStatus

    private val _currentTab = MutableStateFlow(0)
    val currentTab: StateFlow<Int> = _currentTab.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        // Auto-connect if already paired
        if (prefs.isPaired && prefs.pairedPayload != null) {
            connectToHost(prefs.pairedPayload!!)
        }
    }

    fun setTab(tabIndex: Int) {
        _currentTab.value = tabIndex.coerceIn(0, 4)
    }

    fun pairWithPayload(payload: PairingPayload) {
        CrashProtector.safeRun(TAG, Unit) {
            prefs.pairedPayload = payload
            _pairedPayload.value = payload
            _isPaired.value = true
            connectToHost(payload)
        }
    }

    fun pairWithRawString(rawInput: String): Boolean {
        val parsed = PairingPayload.parse(rawInput)
        return if (parsed != null) {
            pairWithPayload(parsed)
            true
        } else {
            _errorMessage.value = "Invalid pairing code or QR format"
            false
        }
    }

    fun connectToHost(payload: PairingPayload) {
        CrashProtector.safeRun(TAG, Unit) {
            Log.d(TAG, "Connecting to host: ${payload.pairingId}")
            webRtcManager.startConnection(payload)
        }
    }

    fun forceReconnect() {
        val payload = _pairedPayload.value ?: return
        webRtcManager.close()
        connectToHost(payload)
    }

    fun unpairAndReset() {
        webRtcManager.close()
        prefs.clearPairing()
        _pairedPayload.value = null
        _isPaired.value = false
        _currentTab.value = 0
    }

    fun clearError() {
        _errorMessage.value = null
    }

    // Touch & Control Actions
    fun sendTap(xPercent: Float, yPercent: Float) {
        webRtcManager.sendTap(xPercent, yPercent)
    }

    fun sendSwipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long = 300L) {
        webRtcManager.sendSwipe(startX, startY, endX, endY, durationMs)
    }

    fun sendGlobalAction(action: String) {
        webRtcManager.sendGlobalAction(action)
    }

    fun sendText(text: String) {
        webRtcManager.sendText(text)
    }

    // File Actions
    fun requestDirectory(path: String) {
        webRtcManager.requestDirectoryListing(path)
    }

    fun downloadFile(path: String) {
        webRtcManager.requestFileDownload(path)
    }

    fun clearFileTransferStatus() {
        webRtcManager.clearFileTransferStatus()
    }

    fun clearNotifications() {
        webRtcManager.clearNotifications()
    }

    override fun onCleared() {
        super.onCleared()
        webRtcManager.close()
    }
}

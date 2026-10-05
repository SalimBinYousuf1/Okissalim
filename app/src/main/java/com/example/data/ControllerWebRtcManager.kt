package com.example.data

import android.content.Context
import android.util.Log
import com.example.model.ConnectionState
import com.example.model.ControlCommand
import com.example.model.FileItem
import com.example.model.NotificationPayload
import com.example.model.PairingPayload
import com.example.model.TelemetryPayload
import com.example.util.CrashProtector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

class ControllerWebRtcManager(
    private val context: Context,
    private val signalingManager: ControllerSignalingManager
) {
    private val TAG = "ControllerWebRtc"
    private val scope = CoroutineScope(Dispatchers.IO)

    var eglBase: EglBase? = null
        private set
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null

    // Channels
    private var controlChannel: DataChannel? = null
    private var fileChannel: DataChannel? = null
    private var notificationChannel: DataChannel? = null
    private var statusChannel: DataChannel? = null

    // Video Track from Ubaid Host
    private val _remoteVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val remoteVideoTrack: StateFlow<VideoTrack?> = _remoteVideoTrack.asStateFlow()

    private val _connectionState = MutableStateFlow(ConnectionState.STANDBY)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _telemetry = MutableStateFlow(TelemetryPayload())
    val telemetry: StateFlow<TelemetryPayload> = _telemetry.asStateFlow()

    private val _notifications = MutableStateFlow<List<NotificationPayload>>(emptyList())
    val notifications: StateFlow<List<NotificationPayload>> = _notifications.asStateFlow()

    private val _currentPath = MutableStateFlow("/storage/emulated/0")
    val currentPath: StateFlow<String> = _currentPath.asStateFlow()

    private val _fileItems = MutableStateFlow<List<FileItem>>(emptyList())
    val fileItems: StateFlow<List<FileItem>> = _fileItems.asStateFlow()

    private val _isLoadingFiles = MutableStateFlow(false)
    val isLoadingFiles: StateFlow<Boolean> = _isLoadingFiles.asStateFlow()

    private val _fileTransferStatus = MutableStateFlow<String?>(null)
    val fileTransferStatus: StateFlow<String?> = _fileTransferStatus.asStateFlow()

    private val pendingCandidates = mutableListOf<IceCandidate>()
    @Volatile
    private var isRemoteDescriptionSet = false

    private var activePairingPayload: PairingPayload? = null
    private var connectionWatchdogJob: Job? = null

    init {
        initWebRtcFactory()
    }

    private fun initWebRtcFactory() {
        CrashProtector.safeRun(TAG, Unit) {
            eglBase = try {
                EglBase.create(null, EglBase.CONFIG_RECORDABLE)
            } catch (_: Exception) {
                EglBase.create()
            }

            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(context)
                    .setEnableInternalTracer(false)
                    .createInitializationOptions()
            )

            val encoderFactory = DefaultVideoEncoderFactory(eglBase?.eglBaseContext, true, true)
            val decoderFactory = DefaultVideoDecoderFactory(eglBase?.eglBaseContext)

            peerConnectionFactory = PeerConnectionFactory.builder()
                .setVideoEncoderFactory(encoderFactory)
                .setVideoDecoderFactory(decoderFactory)
                .createPeerConnectionFactory()
        }
    }

    /**
     * Connects to Ubaid host using the provided PairingPayload.
     */
    fun startConnection(payload: PairingPayload) {
        CrashProtector.safeRun(TAG, Unit) {
            activePairingPayload = payload
            _connectionState.value = ConnectionState.CONNECTING
            signalingManager.updateConnectionState(ConnectionState.CONNECTING)

            // Watch if Ubaid is online
            signalingManager.watchHostProfile(payload.pairingId)
            signalingManager.onHostOnlineStatusChanged = { isOnline ->
                if (!isOnline && _connectionState.value != ConnectionState.CONNECTED) {
                    _connectionState.value = ConnectionState.HOST_UNREACHABLE
                    signalingManager.updateConnectionState(ConnectionState.HOST_UNREACHABLE)
                }
            }

            createPeerConnection(payload)
            setupDataChannels()

            // Setup signaling listeners
            signalingManager.onRemoteAnswerReceived = { sdpAnswer ->
                handleRemoteAnswer(sdpAnswer)
            }
            signalingManager.onRemoteHostCandidateReceived = { sdpMid, sdpMLineIndex, candidate ->
                handleRemoteCandidate(sdpMid, sdpMLineIndex, candidate)
            }
            signalingManager.startListeningForAnswer(payload.pairingId)

            // Create and send WebRTC offer
            createAndSendOffer(payload.pairingId)

            // Start watchdog to monitor connection progress
            startConnectionWatchdog()
        }
    }

    private fun createPeerConnection(payload: PairingPayload) {
        val factory = peerConnectionFactory ?: return
        synchronized(pendingCandidates) {
            pendingCandidates.clear()
            isRemoteDescriptionSet = false
        }

        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun.cloudflare.com:3478").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun.services.mozilla.com:3478").createIceServer()
        )

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED
            iceTransportsType = PeerConnection.IceTransportsType.ALL
        }

        peerConnection = factory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {
                Log.d(TAG, "SignalingState: $state")
            }

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                Log.d(TAG, "IceConnectionState: $state")
                when (state) {
                    PeerConnection.IceConnectionState.CONNECTED -> {
                        _connectionState.value = ConnectionState.CONNECTED
                        signalingManager.updateConnectionState(ConnectionState.CONNECTED)
                        connectionWatchdogJob?.cancel()
                        // Request initial directory
                        requestDirectoryListing("/")
                    }
                    PeerConnection.IceConnectionState.DISCONNECTED -> {
                        _connectionState.value = ConnectionState.RECONNECTING
                        signalingManager.updateConnectionState(ConnectionState.RECONNECTING)
                    }
                    PeerConnection.IceConnectionState.FAILED -> {
                        _connectionState.value = ConnectionState.RECONNECTING
                        signalingManager.updateConnectionState(ConnectionState.RECONNECTING)
                        handleConnectionDrop()
                    }
                    PeerConnection.IceConnectionState.CLOSED -> {
                        _connectionState.value = ConnectionState.STANDBY
                        signalingManager.updateConnectionState(ConnectionState.STANDBY)
                    }
                    else -> {}
                }
            }

            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}

            override fun onIceCandidate(candidate: IceCandidate?) {
                if (candidate != null && activePairingPayload != null) {
                    signalingManager.sendCandidate(
                        activePairingPayload!!.pairingId,
                        candidate.sdpMid,
                        candidate.sdpMLineIndex,
                        candidate.sdp
                    )
                }
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            override fun onAddStream(stream: MediaStream?) {
                if (stream != null && stream.videoTracks.isNotEmpty()) {
                    _remoteVideoTrack.value = stream.videoTracks[0]
                }
            }
            override fun onRemoveStream(stream: MediaStream?) {
                _remoteVideoTrack.value = null
            }
            override fun onDataChannel(dataChannel: DataChannel?) {
                dataChannel?.let { bindDataChannel(it) }
            }
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                if (receiver?.track() is VideoTrack) {
                    _remoteVideoTrack.value = receiver.track() as VideoTrack
                }
            }
        })
    }

    private fun setupDataChannels() {
        val pc = peerConnection ?: return
        val init = DataChannel.Init().apply {
            ordered = true
        }

        controlChannel = pc.createDataChannel("control", init).also { bindDataChannel(it) }
        fileChannel = pc.createDataChannel("file", init).also { bindDataChannel(it) }
        notificationChannel = pc.createDataChannel("notifications", init).also { bindDataChannel(it) }
        statusChannel = pc.createDataChannel("status", init).also { bindDataChannel(it) }
    }

    private fun bindDataChannel(dc: DataChannel) {
        dc.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) {}
            override fun onStateChange() {
                Log.d(TAG, "DataChannel '${dc.label()}' state: ${dc.state()}")
                if (dc.label() == "file" && dc.state() == DataChannel.State.OPEN) {
                    requestDirectoryListing("/")
                }
            }

            override fun onMessage(buffer: DataChannel.Buffer?) {
                if (buffer == null) return
                CrashProtector.safeRun(TAG, Unit) {
                    val bytes = ByteArray(buffer.data.remaining())
                    buffer.data.get(bytes)
                    val text = String(bytes, StandardCharsets.UTF_8)

                    when (dc.label()) {
                        "status" -> {
                            val t = TelemetryPayload.fromJson(text)
                            if (t != null) _telemetry.value = t
                        }
                        "notifications" -> {
                            val notif = NotificationPayload.fromJson(text)
                            if (notif != null) {
                                val current = _notifications.value.toMutableList()
                                if (notif.isRemoved) {
                                    current.removeAll { it.key == notif.key }
                                } else {
                                    current.removeAll { it.key == notif.key }
                                    current.add(0, notif)
                                }
                                _notifications.value = current
                            }
                        }
                        "file" -> {
                            handleFileResponse(text)
                        }
                    }
                }
            }
        })
    }

    private fun createAndSendOffer(pairingId: String) {
        val pc = peerConnection ?: return
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }

        pc.createOffer(object : SdpObserver {
            override fun onCreateSuccess(offerDesc: SessionDescription?) {
                if (offerDesc != null) {
                    pc.setLocalDescription(object : SdpObserver {
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onSetSuccess() {
                            Log.d(TAG, "Local description set. Publishing offer to Firestore...")
                            signalingManager.sendOffer(pairingId, offerDesc.description)
                        }
                        override fun onCreateFailure(err: String?) {
                            Log.e(TAG, "Failed to set local description: $err")
                        }
                        override fun onSetFailure(err: String?) {
                            Log.e(TAG, "Failed to set local description: $err")
                        }
                    }, offerDesc)
                }
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(err: String?) {
                Log.e(TAG, "Failed to create offer: $err")
            }
            override fun onSetFailure(err: String?) {
                Log.e(TAG, "Failed to set offer: $err")
            }
        }, constraints)
    }

    private fun handleRemoteAnswer(sdpAnswer: String) {
        val pc = peerConnection ?: return
        val answerDesc = SessionDescription(SessionDescription.Type.ANSWER, sdpAnswer)
        pc.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() {
                Log.d(TAG, "Remote answer set successfully. Draining candidates...")
                synchronized(pendingCandidates) {
                    isRemoteDescriptionSet = true
                    for (cand in pendingCandidates) {
                        pc.addIceCandidate(cand)
                    }
                    pendingCandidates.clear()
                }
            }
            override fun onCreateFailure(err: String?) {}
            override fun onSetFailure(err: String?) {
                Log.e(TAG, "Failed to set remote answer: $err")
            }
        }, answerDesc)
    }

    private fun handleRemoteCandidate(sdpMid: String, sdpMLineIndex: Int, candidateStr: String) {
        val iceCandidate = IceCandidate(sdpMid, sdpMLineIndex, candidateStr)
        synchronized(pendingCandidates) {
            if (isRemoteDescriptionSet && peerConnection != null) {
                peerConnection?.addIceCandidate(iceCandidate)
            } else {
                pendingCandidates.add(iceCandidate)
            }
        }
    }

    private fun handleConnectionDrop() {
        scope.launch {
            delay(2000L)
            if (isActive && activePairingPayload != null) {
                Log.d(TAG, "Re-initiating connection after drop...")
                startConnection(activePairingPayload!!)
            }
        }
    }

    private fun startConnectionWatchdog() {
        connectionWatchdogJob?.cancel()
        connectionWatchdogJob = scope.launch {
            delay(12000L)
            if (isActive && _connectionState.value == ConnectionState.CONNECTING) {
                Log.w(TAG, "Connection taking long, updating state to WAITING_ON_HOST")
                _connectionState.value = ConnectionState.WAITING_ON_HOST
                signalingManager.updateConnectionState(ConnectionState.WAITING_ON_HOST)
            }
        }
    }

    // --- Control Commands ---

    fun sendTap(xPercent: Float, yPercent: Float) {
        sendCommand(ControlCommand.Tap(xPercent, yPercent))
    }

    fun sendSwipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long = 300L) {
        sendCommand(ControlCommand.Swipe(startX, startY, endX, endY, durationMs))
    }

    fun sendGlobalAction(action: String) {
        sendCommand(ControlCommand.GlobalAction(action))
    }

    fun sendText(text: String) {
        if (text.isNotBlank()) {
            sendCommand(ControlCommand.TextInjection(text))
        }
    }

    private fun sendCommand(command: ControlCommand) {
        val dc = controlChannel
        if (dc != null && dc.state() == DataChannel.State.OPEN) {
            val json = command.toJson()
            val bytes = json.toByteArray(StandardCharsets.UTF_8)
            dc.send(DataChannel.Buffer(ByteBuffer.wrap(bytes), false))
        }
    }

    // --- File Channel ---

    fun requestDirectoryListing(path: String) {
        val dc = fileChannel ?: return
        if (dc.state() != DataChannel.State.OPEN) return
        _isLoadingFiles.value = true
        _currentPath.value = path
        val requestJson = org.json.JSONObject().apply {
            put("action", "list")
            put("path", path)
        }.toString()
        dc.send(DataChannel.Buffer(ByteBuffer.wrap(requestJson.toByteArray(StandardCharsets.UTF_8)), false))
    }

    fun requestFileDownload(filePath: String) {
        val dc = fileChannel ?: return
        if (dc.state() != DataChannel.State.OPEN) return
        _fileTransferStatus.value = "Requesting download: ${filePath.substringAfterLast('/')}..."
        val requestJson = org.json.JSONObject().apply {
            put("action", "read")
            put("path", filePath)
        }.toString()
        dc.send(DataChannel.Buffer(ByteBuffer.wrap(requestJson.toByteArray(StandardCharsets.UTF_8)), false))
    }

    private fun handleFileResponse(text: String) {
        _isLoadingFiles.value = false
        val parsed = FileItem.listFromJson(text)
        if (parsed != null) {
            _currentPath.value = parsed.first
            _fileItems.value = parsed.second
        } else {
            try {
                val obj = org.json.JSONObject(text)
                val type = obj.optString("type")
                if (type == "file_chunk") {
                    val path = obj.optString("path")
                    val isEnd = obj.optBoolean("isEnd", false)
                    _fileTransferStatus.value = if (isEnd) "Download completed: ${path.substringAfterLast('/')}" else "Receiving chunks..."
                } else if (type == "file_error") {
                    _fileTransferStatus.value = "Error: ${obj.optString("error", "Unknown")}"
                }
            } catch (_: Exception) {}
        }
    }

    fun clearNotifications() {
        _notifications.value = emptyList()
    }

    fun clearFileTransferStatus() {
        _fileTransferStatus.value = null
    }

    fun close() {
        connectionWatchdogJob?.cancel()
        peerConnection?.close()
        peerConnection = null
        signalingManager.stopSignaling()
        _remoteVideoTrack.value = null
        _connectionState.value = ConnectionState.STANDBY
    }
}

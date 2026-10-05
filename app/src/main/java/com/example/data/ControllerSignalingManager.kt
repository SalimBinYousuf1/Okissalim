package com.example.data

import android.content.Context
import android.util.Log
import com.example.model.ConnectionState
import com.example.model.FirebaseConfigStatus
import com.example.util.CrashProtector
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ControllerSignalingManager(private val context: Context) {
    private val TAG = "ControllerSignaling"
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _connectionState = MutableStateFlow(ConnectionState.STANDBY)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _firebaseStatus = MutableStateFlow<FirebaseConfigStatus>(
        FirebaseConfigStatus.Missing("Not initialized")
    )
    val firebaseStatus: StateFlow<FirebaseConfigStatus> = _firebaseStatus.asStateFlow()

    private var firestore: FirebaseFirestore? = null
    private var answerListener: ListenerRegistration? = null
    private var candidateListener: ListenerRegistration? = null
    private var hostProfileListener: ListenerRegistration? = null

    var onRemoteAnswerReceived: ((sdp: String) -> Unit)? = null
    var onRemoteHostCandidateReceived: ((sdpMid: String, sdpMLineIndex: Int, candidate: String) -> Unit)? = null
    var onHostOnlineStatusChanged: ((isOnline: Boolean) -> Unit)? = null

    init {
        initFirebase()
    }

    fun initFirebase(): FirebaseConfigStatus {
        return CrashProtector.safeRun(TAG, FirebaseConfigStatus.Missing("Firebase initialization failed")) {
            try {
                val app = if (FirebaseApp.getApps(context).isEmpty()) {
                    try {
                        FirebaseApp.initializeApp(context)
                    } catch (e: Exception) {
                        val fallbackOptions = FirebaseOptions.Builder()
                            .setApplicationId("1:428293373821:android:764070a9c93f569c629614")
                            .setApiKey("AIzaSyBdiTj7YRtZ5ncYv6few_Gfaw9h-mbqU3w")
                            .setProjectId("salim-x-ubaid")
                            .setStorageBucket("salim-x-ubaid.firebasestorage.app")
                            .build()
                        FirebaseApp.initializeApp(context, fallbackOptions)
                    }
                } else {
                    FirebaseApp.getInstance()
                }

                if (app != null) {
                    firestore = FirebaseFirestore.getInstance(app)
                    val status = FirebaseConfigStatus.Configured(app.options.projectId ?: "salim-x-ubaid")
                    _firebaseStatus.value = status
                    status
                } else {
                    val status = FirebaseConfigStatus.Missing("Firebase configuration missing")
                    _firebaseStatus.value = status
                    status
                }
            } catch (e: Exception) {
                Log.e(TAG, "Firebase initialization error: ${e.message}", e)
                val status = FirebaseConfigStatus.Missing(e.message ?: "Configuration error")
                _firebaseStatus.value = status
                status
            }
        }
    }

    /**
     * Watches host profile to know if Ubaid is reachable or offline.
     */
    fun watchHostProfile(pairingId: String) {
        val db = firestore ?: return
        hostProfileListener?.remove()
        hostProfileListener = db.collection("host_profiles").document(pairingId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Host profile listener error: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val status = snapshot.getString("status") ?: "offline"
                    val isOnline = (status == "online")
                    onHostOnlineStatusChanged?.invoke(isOnline)
                } else {
                    onHostOnlineStatusChanged?.invoke(false)
                }
            }
    }

    /**
     * Publishes the WebRTC offer from Salim Controller to Ubaid.
     */
    fun sendOffer(pairingId: String, sdpOffer: String) {
        val db = firestore ?: return
        scope.launch {
            CrashProtector.safeRun(TAG, Unit) {
                val offerMap = hashMapOf(
                    "type" to "offer",
                    "sdp" to sdpOffer,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                db.collection("sessions").document(pairingId)
                    .collection("signaling").document("offer")
                    .set(offerMap)
                    .addOnSuccessListener {
                        Log.d(TAG, "WebRTC offer published to Firestore for $pairingId")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to publish WebRTC offer: ${e.message}", e)
                    }
            }
        }
    }

    /**
     * Starts listening for the WebRTC answer and ICE candidates from Ubaid.
     */
    fun startListeningForAnswer(pairingId: String) {
        val db = firestore ?: return
        CrashProtector.safeRun(TAG, Unit) {
            val sessionDoc = db.collection("sessions").document(pairingId)

            // Listen for Ubaid's answer
            answerListener?.remove()
            answerListener = sessionDoc.collection("signaling").document("answer")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Answer listener error: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null && snapshot.exists()) {
                        val sdp = snapshot.getString("sdp")
                        val type = snapshot.getString("type")
                        if (!sdp.isNullOrBlank() && type == "answer") {
                            Log.d(TAG, "Received WebRTC answer from Ubaid!")
                            onRemoteAnswerReceived?.invoke(sdp)
                        }
                    }
                }

            // Listen for Ubaid's ICE candidates
            candidateListener?.remove()
            candidateListener = sessionDoc.collection("host_candidates")
                .addSnapshotListener { snapshots, error ->
                    if (error != null || snapshots == null) return@addSnapshotListener
                    for (docChange in snapshots.documentChanges) {
                        val data = docChange.document.data
                        val sdpMid = data["sdpMid"] as? String ?: ""
                        val sdpMLineIndex = (data["sdpMLineIndex"] as? Long)?.toInt() ?: 0
                        val candidate = data["candidate"] as? String ?: ""
                        if (candidate.isNotBlank()) {
                            onRemoteHostCandidateReceived?.invoke(sdpMid, sdpMLineIndex, candidate)
                        }
                    }
                }
        }
    }

    /**
     * Sends local controller ICE candidate to Firestore for Ubaid to discover.
     */
    fun sendCandidate(pairingId: String, sdpMid: String, sdpMLineIndex: Int, candidate: String) {
        val db = firestore ?: return
        CrashProtector.safeRun(TAG, Unit) {
            val candidateMap = hashMapOf(
                "sdpMid" to sdpMid,
                "sdpMLineIndex" to sdpMLineIndex,
                "candidate" to candidate,
                "createdAt" to FieldValue.serverTimestamp()
            )
            db.collection("sessions").document(pairingId)
                .collection("controller_candidates")
                .add(candidateMap)
        }
    }

    fun updateConnectionState(state: ConnectionState) {
        _connectionState.value = state
    }

    fun stopSignaling() {
        answerListener?.remove()
        answerListener = null
        candidateListener?.remove()
        candidateListener = null
        hostProfileListener?.remove()
        hostProfileListener = null
        _connectionState.value = ConnectionState.STANDBY
    }
}

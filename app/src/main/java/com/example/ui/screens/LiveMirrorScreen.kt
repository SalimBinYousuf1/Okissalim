package com.example.ui.screens

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.model.ConnectionState
import com.example.ui.theme.AccentPrimary
import com.example.ui.theme.BorderStrong
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.ControllerViewModel
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer

@Composable
fun LiveMirrorScreen(
    viewModel: ControllerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val connectionState by viewModel.connectionState.collectAsState()
    val remoteVideoTrack by viewModel.remoteVideoTrack.collectAsState()
    val pairedPayload by viewModel.pairedPayload.collectAsState()

    var showKeyboardDialog by remember { mutableStateOf(false) }
    var scaleType by remember { mutableStateOf(RendererCommon.ScalingType.SCALE_ASPECT_FIT) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .testTag("live_mirror_screen")
    ) {
        val containerWidth = constraints.maxWidth.toFloat()
        val containerHeight = constraints.maxHeight.toFloat()

        // Host aspect ratio (default 1080x2400)
        val hostWidth = pairedPayload?.screenWidth ?: 1080
        val hostHeight = pairedPayload?.screenHeight ?: 2400
        val hostAspectRatio = hostWidth.toFloat() / hostHeight.toFloat()
        val containerAspectRatio = containerWidth / containerHeight

        // Calculate actual rendered video frame dimensions and letterbox offsets
        val (videoWidth, videoHeight, offsetX, offsetY) = remember(containerWidth, containerHeight, hostAspectRatio, scaleType) {
            if (scaleType == RendererCommon.ScalingType.SCALE_ASPECT_FIT) {
                if (containerAspectRatio > hostAspectRatio) {
                    val w = containerHeight * hostAspectRatio
                    val x = (containerWidth - w) / 2f
                    listOf(w, containerHeight, x, 0f)
                } else {
                    val h = containerWidth / hostAspectRatio
                    val y = (containerHeight - h) / 2f
                    listOf(containerWidth, h, 0f, y)
                }
            } else {
                listOf(containerWidth, containerHeight, 0f, 0f)
            }
        }

        // Gesture state
        var dragStartOffset by remember { mutableStateOf<Offset?>(null) }
        var dragStartTime by remember { mutableStateOf(0L) }

        if (remoteVideoTrack != null && connectionState == ConnectionState.CONNECTED) {
            // Live Video Surface + Touch Interceptor
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(containerWidth, containerHeight, videoWidth, videoHeight, offsetX, offsetY) {
                        detectTapGestures { tapOffset ->
                            // Convert touch coordinate to percentage of Ubaid's real screen
                            val xPercent = ((tapOffset.x - offsetX) / videoWidth).coerceIn(0f, 1f)
                            val yPercent = ((tapOffset.y - offsetY) / videoHeight).coerceIn(0f, 1f)
                            viewModel.sendTap(xPercent, yPercent)
                        }
                    }
                    .pointerInput(containerWidth, containerHeight, videoWidth, videoHeight, offsetX, offsetY) {
                        detectDragGestures(
                            onDragStart = { startOffset ->
                                dragStartOffset = startOffset
                                dragStartTime = System.currentTimeMillis()
                            },
                            onDragEnd = {
                                val start = dragStartOffset
                                if (start != null) {
                                    val duration = (System.currentTimeMillis() - dragStartTime).coerceAtLeast(100L)
                                    val startXPercent = ((start.x - offsetX) / videoWidth).coerceIn(0f, 1f)
                                    val startYPercent = ((start.y - offsetY) / videoHeight).coerceIn(0f, 1f)
                                    // Default swipe velocity
                                    val endXPercent = startXPercent
                                    val endYPercent = (startYPercent - 0.25f).coerceIn(0f, 1f)
                                    viewModel.sendSwipe(startXPercent, startYPercent, endXPercent, endYPercent, duration)
                                }
                                dragStartOffset = null
                            },
                            onDragCancel = {
                                dragStartOffset = null
                            },
                            onDrag = { change, _ ->
                                change.consume()
                            }
                        )
                    }
            ) {
                AndroidView(
                    factory = { ctx ->
                        val renderer = SurfaceViewRenderer(ctx).apply {
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            setEnableHardwareScaler(true)
                            viewModel.webRtcManager.eglBase?.eglBaseContext?.let {
                                init(it, null)
                            }
                            setScalingType(scaleType)
                        }
                        remoteVideoTrack?.addSink(renderer)
                        renderer
                    },
                    update = { renderer ->
                        renderer.setScalingType(scaleType)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            // Connection / Video Waiting Placeholder (Clear, honest status)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (connectionState == ConnectionState.CONNECTING || connectionState == ConnectionState.RECONNECTING) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(32.dp),
                            strokeWidth = 3.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.ScreenShare,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = connectionState.displayLabel,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = (-0.3).sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = when (connectionState) {
                        ConnectionState.CONNECTED -> "Awaiting video frame from Ubaid..."
                        ConnectionState.CONNECTING -> "Negotiating peer-to-peer WebRTC connection..."
                        ConnectionState.RECONNECTING -> "Attempting ICE restart..."
                        ConnectionState.WAITING_ON_HOST -> "Ubaid has not started screen streaming. Make sure broadcast is active on Ubaid."
                        ConnectionState.HOST_UNREACHABLE -> "Ubaid is offline or unreachable."
                        ConnectionState.STANDBY -> "Ready to connect."
                    },
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                if (connectionState != ConnectionState.CONNECTING && connectionState != ConnectionState.CONNECTED) {
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = { viewModel.forceReconnect() },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Reconnect", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }

        // Tactile Control Bar Overlay (Frosted High-Contrast Pill)
        // Positioned neatly at the bottom edge with margins so it never obscures critical host views
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xE61E293B))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ControlPillButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    label = "Back",
                    onClick = { viewModel.sendGlobalAction("BACK") }
                )
                ControlPillButton(
                    icon = Icons.Default.Home,
                    label = "Home",
                    onClick = { viewModel.sendGlobalAction("HOME") }
                )
                ControlPillButton(
                    icon = Icons.Default.Layers,
                    label = "Recents",
                    onClick = { viewModel.sendGlobalAction("RECENTS") }
                )
                ControlPillButton(
                    icon = Icons.Default.Keyboard,
                    label = "Text",
                    onClick = { showKeyboardDialog = true }
                )
                ControlPillButton(
                    icon = Icons.Default.AspectRatio,
                    label = if (scaleType == RendererCommon.ScalingType.SCALE_ASPECT_FIT) "Fit" else "Fill",
                    onClick = {
                        scaleType = if (scaleType == RendererCommon.ScalingType.SCALE_ASPECT_FIT) {
                            RendererCommon.ScalingType.SCALE_ASPECT_FILL
                        } else {
                            RendererCommon.ScalingType.SCALE_ASPECT_FIT
                        }
                    }
                )
            }
        }
    }

    // Text Input Dialog
    if (showKeyboardDialog) {
        var textToSend by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showKeyboardDialog = false },
            title = {
                Text(
                    text = "Send Text to Ubaid",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Types directly into whatever field is currently focused on Ubaid.",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                    OutlinedTextField(
                        value = textToSend,
                        onValueChange = { textToSend = it },
                        placeholder = { Text("Type message or URL...") },
                        singleLine = false,
                        maxLines = 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("remote_text_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentPrimary,
                            unfocusedBorderColor = BorderStrong
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (textToSend.isNotBlank()) {
                            viewModel.sendText(textToSend)
                            showKeyboardDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("send_remote_text_button")
                ) {
                    Text("Send to Host", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showKeyboardDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
private fun ControlPillButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.12f))
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color.White,
            modifier = Modifier.size(20.dp)
        )
    }
}

package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ConnectionState
import com.example.ui.theme.AccentContainer
import com.example.ui.theme.AccentPrimary
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.StatusConnectedBg
import com.example.ui.theme.StatusConnectedBorder
import com.example.ui.theme.StatusConnectedDot
import com.example.ui.theme.StatusConnectedText
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.viewmodel.ControllerViewModel

@Composable
fun HostStatusScreen(
    viewModel: ControllerViewModel,
    modifier: Modifier = Modifier
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val telemetry by viewModel.telemetry.collectAsState()
    val pairedPayload by viewModel.pairedPayload.collectAsState()
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .verticalScroll(scrollState)
            .testTag("host_status_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Title
        Column {
            Text(
                text = "Host Telemetry & Health",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp,
                color = TextPrimary
            )
            Text(
                text = "Real-time vitals reported by Ubaid over WebRTC status channel",
                fontSize = 12.sp,
                color = TextSecondary
            )
        }

        // Live Telemetry Cards Grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TelemetryMetricCard(
                modifier = Modifier.weight(1f),
                icon = if (telemetry.isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryFull,
                iconTint = if (telemetry.batteryPercent > 20) AccentPrimary else Color(0xFFEF4444),
                title = "Battery",
                value = "${telemetry.batteryPercent}%",
                subtitle = if (telemetry.isCharging) "Charging active" else "On battery power"
            )

            TelemetryMetricCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Wifi,
                iconTint = AccentPrimary,
                title = "Network",
                value = telemetry.networkType,
                subtitle = if (telemetry.networkConnected) "Connected online" else "No internet"
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TelemetryMetricCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.PhoneAndroid,
                iconTint = AccentPrimary,
                title = "Host Display",
                value = if (telemetry.isScreenOn) "Screen On" else "Screen Off",
                subtitle = "${pairedPayload?.screenWidth ?: 1080} x ${pairedPayload?.screenHeight ?: 2400}"
            )

            TelemetryMetricCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Speed,
                iconTint = if (connectionState == ConnectionState.CONNECTED) StatusConnectedDot else TextSecondary,
                title = "Connection",
                value = if (connectionState == ConnectionState.CONNECTED) "P2P Active" else "Pending",
                subtitle = connectionState.displayLabel
            )
        }

        // Host Device Specs Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFFF8FAFC))
                .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "DEVICE PROFILE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentPrimary,
                    letterSpacing = 1.sp
                )

                ProfileSpecRow(label = "Host Device Name", value = pairedPayload?.hostName ?: "Ubaid Host")
                ProfileSpecRow(label = "Pairing ID", value = pairedPayload?.pairingId ?: "--", isMonospace = true)
                ProfileSpecRow(label = "Screen Density", value = "${pairedPayload?.densityDpi ?: 420} DPI")
                ProfileSpecRow(label = "Active DataChannels", value = "Control, Files, Alerts, Telemetry")
                ProfileSpecRow(label = "Signaling Server", value = "Firebase Firestore (Free Tier)")
            }
        }
    }
}

@Composable
private fun TelemetryMetricCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    iconTint: Color,
    title: String,
    value: String,
    subtitle: String
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFF8FAFC))
            .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(AccentContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = value,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.4).sp,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = TextTertiary
            )
        }
    }
}

@Composable
private fun ProfileSpecRow(
    label: String,
    value: String,
    isMonospace: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 13.sp, color = TextSecondary)
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = if (isMonospace) FontFamily.Monospace else FontFamily.Default,
            color = TextPrimary
        )
    }
}

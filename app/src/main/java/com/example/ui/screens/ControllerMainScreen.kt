package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
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
import com.example.ui.theme.StatusErrorBg
import com.example.ui.theme.StatusErrorBorder
import com.example.ui.theme.StatusErrorDot
import com.example.ui.theme.StatusErrorText
import com.example.ui.theme.StatusReconnectingBg
import com.example.ui.theme.StatusReconnectingBorder
import com.example.ui.theme.StatusReconnectingDot
import com.example.ui.theme.StatusReconnectingText
import com.example.ui.theme.StatusStandbyBg
import com.example.ui.theme.StatusStandbyBorder
import com.example.ui.theme.StatusStandbyDot
import com.example.ui.theme.StatusStandbyText
import com.example.ui.theme.StatusWaitingBg
import com.example.ui.theme.StatusWaitingBorder
import com.example.ui.theme.StatusWaitingDot
import com.example.ui.theme.StatusWaitingText
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.viewmodel.ControllerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControllerMainScreen(
    viewModel: ControllerViewModel,
    modifier: Modifier = Modifier
) {
    val currentTab by viewModel.currentTab.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val telemetry by viewModel.telemetry.collectAsState()

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
            .testTag("controller_main_screen"),
        containerColor = Color.White,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(AccentContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ScreenShare,
                                    contentDescription = null,
                                    tint = AccentPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Salim",
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.4).sp,
                                color = TextPrimary
                            )
                        }

                        // Right: Battery indicator + Connection Capsule
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (connectionState == ConnectionState.CONNECTED) {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFF1F5F9))
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (telemetry.isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryFull,
                                        contentDescription = null,
                                        tint = if (telemetry.batteryPercent > 20) AccentPrimary else Color(0xFFEF4444),
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${telemetry.batteryPercent}%",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                }
                            }

                            ConnectionCapsule(state = connectionState)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = Color.White,
                tonalElevation = 0.dp,
                modifier = Modifier
                    .navigationBarsPadding()
                    .border(1.dp, BorderSubtle, RoundedCornerShape(0.dp))
            ) {
                NavigationTabItem(
                    icon = Icons.Default.ScreenShare,
                    label = "Mirror",
                    selected = currentTab == 0,
                    onClick = { viewModel.setTab(0) }
                )
                NavigationTabItem(
                    icon = Icons.Default.Folder,
                    label = "Files",
                    selected = currentTab == 1,
                    onClick = { viewModel.setTab(1) }
                )
                NavigationTabItem(
                    icon = Icons.Default.Notifications,
                    label = "Alerts",
                    selected = currentTab == 2,
                    onClick = { viewModel.setTab(2) }
                )
                NavigationTabItem(
                    icon = Icons.Default.Speed,
                    label = "Status",
                    selected = currentTab == 3,
                    onClick = { viewModel.setTab(3) }
                )
                NavigationTabItem(
                    icon = Icons.Default.Settings,
                    label = "Settings",
                    selected = currentTab == 4,
                    onClick = { viewModel.setTab(4) }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = currentTab,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "tab_transition"
            ) { tab ->
                when (tab) {
                    0 -> LiveMirrorScreen(viewModel = viewModel)
                    1 -> FileBrowserScreen(viewModel = viewModel)
                    2 -> NotificationFeedScreen(viewModel = viewModel)
                    3 -> HostStatusScreen(viewModel = viewModel)
                    4 -> ControllerSettingsScreen(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.NavigationTabItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    NavigationBarItem(
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(22.dp)
            )
        },
        label = {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
        },
        selected = selected,
        onClick = onClick,
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = AccentPrimary,
            selectedTextColor = AccentPrimary,
            unselectedIconColor = TextTertiary,
            unselectedTextColor = TextTertiary,
            indicatorColor = AccentContainer
        )
    )
}

@Composable
private fun ConnectionCapsule(state: ConnectionState) {
    val (bgColor, borderColor, dotColor, textColor) = when (state) {
        ConnectionState.CONNECTED -> listOf(StatusConnectedBg, StatusConnectedBorder, StatusConnectedDot, StatusConnectedText)
        ConnectionState.CONNECTING -> listOf(StatusReconnectingBg, StatusReconnectingBorder, StatusReconnectingDot, StatusReconnectingText)
        ConnectionState.RECONNECTING -> listOf(StatusReconnectingBg, StatusReconnectingBorder, StatusReconnectingDot, StatusReconnectingText)
        ConnectionState.WAITING_ON_HOST -> listOf(StatusWaitingBg, StatusWaitingBorder, StatusWaitingDot, StatusWaitingText)
        ConnectionState.HOST_UNREACHABLE -> listOf(StatusErrorBg, StatusErrorBorder, StatusErrorDot, StatusErrorText)
        ConnectionState.STANDBY -> listOf(StatusStandbyBg, StatusStandbyBorder, StatusStandbyDot, StatusStandbyText)
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = when (state) {
                ConnectionState.CONNECTED -> "Streaming"
                ConnectionState.CONNECTING -> "Connecting"
                ConnectionState.RECONNECTING -> "Reconnecting"
                ConnectionState.WAITING_ON_HOST -> "Waiting"
                ConnectionState.HOST_UNREACHABLE -> "Offline"
                ConnectionState.STANDBY -> "Idle"
            },
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}

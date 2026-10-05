package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.ControllerPreferences
import com.example.model.ConnectionState
import com.example.model.ControlCommand
import com.example.model.FileItem
import com.example.model.NotificationPayload
import com.example.model.PairingPayload
import com.example.model.TelemetryPayload
import com.example.util.CrashProtector
import com.example.util.PermissionHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context verifies Salim app name`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Salim", appName)
    }

    @Test
    fun `pairing payload json serialization roundtrip`() {
        val payload = PairingPayload(
            pairingId = "UBAID-TEST-1234",
            hostName = "Test Host Phone",
            projectId = "salim-x-ubaid",
            apiKey = "AIzaSyTestKey1234",
            storageBucket = "salim-x-ubaid.firebasestorage.app",
            screenWidth = 1080,
            screenHeight = 2400,
            densityDpi = 420
        )
        val json = payload.toJson()
        val restored = PairingPayload.fromJson(json)

        assertNotNull(restored)
        assertEquals(payload.pairingId, restored?.pairingId)
        assertEquals(payload.hostName, restored?.hostName)
        assertEquals(payload.projectId, restored?.projectId)
        assertEquals(payload.screenWidth, restored?.screenWidth)
        assertEquals(payload.screenHeight, restored?.screenHeight)
        assertEquals(payload.densityDpi, restored?.densityDpi)
    }

    @Test
    fun `pairing payload parses manual code string`() {
        val rawCode = "ubaid-7f3a2b1c"
        val parsed = PairingPayload.parse(rawCode)
        assertNotNull(parsed)
        assertEquals("UBAID-7F3A2B1C", parsed?.pairingId)

        val rawShort = "ABC123"
        val parsedShort = PairingPayload.parse(rawShort)
        assertNotNull(parsedShort)
        assertEquals("UBAID-ABC123", parsedShort?.pairingId)

        val invalid = PairingPayload.parse("   ")
        assertNull(invalid)
    }

    @Test
    fun `control command tap parser and serialization`() {
        val tapCmd = ControlCommand.Tap(0.45f, 0.75f)
        val json = tapCmd.toJson()
        assertTrue(json.contains("\"tap\""))

        val parsed = ControlCommand.fromJson(json)
        assertTrue(parsed is ControlCommand.Tap)
        val tap = parsed as ControlCommand.Tap
        assertEquals(0.45f, tap.xPercent, 0.001f)
        assertEquals(0.75f, tap.yPercent, 0.001f)
    }

    @Test
    fun `control command swipe parser and serialization`() {
        val swipeCmd = ControlCommand.Swipe(0.5f, 0.8f, 0.5f, 0.2f, 250L)
        val json = swipeCmd.toJson()
        assertTrue(json.contains("\"swipe\""))

        val parsed = ControlCommand.fromJson(json)
        assertTrue(parsed is ControlCommand.Swipe)
        val swipe = parsed as ControlCommand.Swipe
        assertEquals(0.5f, swipe.startXPercent, 0.001f)
        assertEquals(0.8f, swipe.startYPercent, 0.001f)
        assertEquals(0.5f, swipe.endXPercent, 0.001f)
        assertEquals(0.2f, swipe.endYPercent, 0.001f)
        assertEquals(250L, swipe.durationMs)
    }

    @Test
    fun `control command global action parser and serialization`() {
        val backCmd = ControlCommand.GlobalAction("BACK")
        val json = backCmd.toJson()
        assertTrue(json.contains("\"BACK\""))

        val parsed = ControlCommand.fromJson(json)
        assertTrue(parsed is ControlCommand.GlobalAction)
        val global = parsed as ControlCommand.GlobalAction
        assertEquals("BACK", global.actionName)
    }

    @Test
    fun `control command text injection parser and serialization`() {
        val textCmd = ControlCommand.TextInjection("Remote typing test from Salim")
        val json = textCmd.toJson()
        assertTrue(json.contains("\"text\""))

        val parsed = ControlCommand.fromJson(json)
        assertTrue(parsed is ControlCommand.TextInjection)
        val text = parsed as ControlCommand.TextInjection
        assertEquals("Remote typing test from Salim", text.text)
    }

    @Test
    fun `notification payload serialization and parsing`() {
        val notif = NotificationPayload(
            key = "msg_001",
            packageName = "com.google.android.apps.messaging",
            appName = "Messages",
            title = "Salim",
            text = "Hello Ubaid, connection active",
            postTime = 123456789L,
            isRemoved = false
        )
        val jsonStr = notif.toJson()
        assertTrue(jsonStr.contains("notification_posted"))
        assertTrue(jsonStr.contains("Messages"))
        assertTrue(jsonStr.contains("Salim"))

        val parsed = NotificationPayload.fromJson(jsonStr)
        assertNotNull(parsed)
        assertEquals("msg_001", parsed?.key)
        assertEquals("Messages", parsed?.appName)
        assertEquals("Salim", parsed?.title)
        assertFalse(parsed?.isRemoved ?: true)
    }

    @Test
    fun `file item serialization and listFromJson parsing`() {
        val item1 = FileItem("Documents", "/storage/emulated/0/Documents", isDirectory = true, sizeBytes = 0L, lastModifiedMs = 1000L)
        val item2 = FileItem("photo.jpg", "/storage/emulated/0/photo.jpg", isDirectory = false, sizeBytes = 2048000L, lastModifiedMs = 2000L)

        val listJson = FileItem.listToJson("/storage/emulated/0", listOf(item1, item2))
        val parsed = FileItem.listFromJson(listJson)

        assertNotNull(parsed)
        assertEquals("/storage/emulated/0", parsed?.first)
        assertEquals(2, parsed?.second?.size)
        assertEquals("Documents", parsed?.second?.get(0)?.name)
        assertTrue(parsed?.second?.get(0)?.isDirectory ?: false)
        assertEquals("photo.jpg", parsed?.second?.get(1)?.name)
        assertFalse(parsed?.second?.get(1)?.isDirectory ?: true)
        assertTrue(parsed?.second?.get(1)?.formattedSize?.contains("MB") ?: false)
    }

    @Test
    fun `telemetry payload json parsing and defaults`() {
        val json = """{"type":"status_telemetry","batteryPercent":85,"isCharging":true,"networkType":"WiFi 6","networkConnected":true,"isScreenOn":true,"timestamp":1700000000}"""
        val telemetry = TelemetryPayload.fromJson(json)

        assertNotNull(telemetry)
        assertEquals(85, telemetry?.batteryPercent)
        assertTrue(telemetry?.isCharging ?: false)
        assertEquals("WiFi 6", telemetry?.networkType)
        assertTrue(telemetry?.networkConnected ?: false)
        assertTrue(telemetry?.isScreenOn ?: false)
    }

    @Test
    fun `connection state labels verification`() {
        assertEquals("Connected & Streaming", ConnectionState.CONNECTED.displayLabel)
        assertEquals("Connecting to Ubaid...", ConnectionState.CONNECTING.displayLabel)
        assertEquals("Reconnecting (ICE)...", ConnectionState.RECONNECTING.displayLabel)
        assertEquals("Waiting on Ubaid", ConnectionState.WAITING_ON_HOST.displayLabel)
        assertEquals("Ubaid Unreachable", ConnectionState.HOST_UNREACHABLE.displayLabel)
        assertEquals("Disconnected", ConnectionState.STANDBY.displayLabel)
    }

    @Test
    fun `controller preferences persistence and reset`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = ControllerPreferences.getInstance(context)

        // Clear initial
        prefs.clearPairing()
        assertFalse(prefs.isPaired)
        assertNull(prefs.pairedPayload)

        // Set pairing
        val payload = PairingPayload(
            pairingId = "UBAID-TEST-PERSIST",
            hostName = "Home Phone",
            projectId = "salim-x-ubaid",
            apiKey = "key",
            storageBucket = "bucket",
            screenWidth = 1080,
            screenHeight = 2400,
            densityDpi = 440
        )
        prefs.pairedPayload = payload
        assertTrue(prefs.isPaired)
        assertNotNull(prefs.pairedPayload)
        assertEquals("UBAID-TEST-PERSIST", prefs.pairedPayload?.pairingId)

        // Clear again
        prefs.clearPairing()
        assertFalse(prefs.isPaired)
        assertNull(prefs.pairedPayload)
    }

    @Test
    fun `crash protector safeRun returns fallback on exception`() {
        val result = CrashProtector.safeRun("TestTag", fallback = "FALLBACK") {
            throw RuntimeException("Simulated error in test")
        }
        assertEquals("FALLBACK", result)
    }

    @Test
    fun `permission helper app settings intent creation`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = PermissionHelper.createAppSettingsIntent(context)
        assertNotNull(intent)
        assertEquals(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent.action)
        assertTrue(intent.data?.toString()?.contains(context.packageName) ?: false)
    }
}

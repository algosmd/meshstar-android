package net.meshstar

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.bitchat.android.MainActivity
import org.junit.Rule
import org.junit.Test

class SatelliteUiInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun unifiedHomeAndContactQrAreReachableWithoutBluetoothOrLocation() {
        compose.waitUntil(20_000) { compose.onAllNodesWithTag("meshstar.unified").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Bluetooth").assertExists()
        compose.onNodeWithText("LoRa").assertExists()
        compose.onNodeWithText("Satellite").assertExists()
        compose.onNodeWithContentDescription("my contact QR").performClick()
        compose.onNodeWithText("Copy QR").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Share").assertExists()
        compose.onNodeWithContentDescription("add").performClick()
        compose.onNodeWithText("add contact").performClick()
        compose.onNodeWithText("paste link").assertExists()
    }
}

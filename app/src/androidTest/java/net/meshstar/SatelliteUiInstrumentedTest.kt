package net.meshstar

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import net.meshstar.ui.SatelliteActivity
import org.junit.Rule
import org.junit.Test

class SatelliteUiInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<SatelliteActivity>()
    @Test fun qrSettingsAndContactImportAreReachableWithoutBluetoothOrLocation() {
        compose.waitUntil(20_000) { compose.onAllNodesWithText("My QR").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("My QR").performClick()
        compose.onNodeWithContentDescription("My contact QR").assertExists()
        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Save and check").assertExists()
        compose.onNodeWithText("Internet server URL").performScrollTo().assertExists()
        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithText("Add contact / Scan QR").performClick()
        compose.onNodeWithText("QR text or MeshStar contact link").assertExists()
        compose.onNodeWithText("Scan QR").performClick()
        compose.onNodeWithText("Allow camera").assertExists()
    }
}

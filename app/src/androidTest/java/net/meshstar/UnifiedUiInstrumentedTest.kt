package net.meshstar

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import net.meshstar.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class UnifiedUiInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    @Test fun oneContactRowPerIdentityAndRoutesAndOneTapVerification() {
        val actions = mutableListOf<UiAction>()
        compose.setContent { MeshTheme(false) { UnifiedScreen(unifiedFixture(), { actions += it; true }) } }
        compose.onAllNodesWithTag("peer.alice").assertCountEquals(1)
        compose.onNodeWithTag("peer.alice").performClick()
        compose.onNodeWithTag("meshstar.route").performClick()
        listOf("Auto", "LoRa", "Relay", "Satellite", "Internet").forEach { compose.onAllNodesWithText(it).onFirst().assertExists() }
        compose.onNodeWithText("Internet").performClick()
        compose.onNodeWithTag("meshstar.route").assertTextContains("Internet", substring = true)
        compose.onNodeWithContentDescription("contact").performClick()
        compose.onNodeWithTag("meshstar.verify").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(actions.contains(UiAction.Verify("alice", true))) }
    }
    @Test fun headerQrPasteAndSettingsUseOneNavigationShell() {
        compose.setContent { MeshTheme(false) { UnifiedScreen(unifiedFixture(), { true }) } }
        compose.onNodeWithContentDescription("my contact QR").performClick()
        compose.onNodeWithText("Copy QR").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Share").assertIsDisplayed()
        compose.onNodeWithContentDescription("add").performClick()
        compose.onNodeWithText("add contact").performClick()
        compose.onNodeWithText("paste link").assertIsDisplayed()
        compose.onNodeWithTag("meshstar.link.input").performTextInput("meshstar://contact?qr=synthetic")
        compose.onNodeWithText("open link").assertIsDisplayed()
    }
    @Test fun localizedScannerKeepsActivityOwnerWithoutRequestingCamera() {
        compose.setContent {
            val base = androidx.compose.ui.platform.LocalContext.current
            val translated = remember { localizedContext(base, "ru") }
            CompositionLocalProvider(androidx.compose.ui.platform.LocalContext provides translated) {
                MeshTheme(false) { UnifiedScreen(unifiedFixture(), { true }, initialPage = "scan") }
            }
        }
        compose.onNodeWithText("вставить ссылку").assertIsDisplayed()
        compose.onNodeWithText("разрешить камеру").assertIsDisplayed()
    }

}

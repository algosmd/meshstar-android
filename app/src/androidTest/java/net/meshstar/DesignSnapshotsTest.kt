package net.meshstar

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import net.meshstar.ui.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Locale

class DesignSnapshotsTest {
    @get:Rule val compose = createComposeRule()
    data class Scene(val locale: String = "en", val dark: Boolean = false, val page: String = "chats",
                     val tab: ChatTab = ChatTab.BLUETOOTH, val routes: Boolean = false, val scale: Float = 1f)
    @Test fun localizedDesignMatrix() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("meshstarSnapshots") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var scene by mutableStateOf(Scene())
        compose.setContent {
            val activityContext = LocalContext.current
            val localized = remember(scene.locale) { localizedContext(activityContext, scene.locale) }
            val density = LocalDensity.current
            CompositionLocalProvider(LocalContext provides localized, LocalDensity provides Density(density.density, scene.scale)) {
                key(scene) { MeshTheme(scene.dark) {
                    UnifiedScreen(unifiedFixture(), { true }, language = scene.locale,
                        requestedPeer = if (scene.page in listOf("chat", "contact")) "alice" else "",
                        initialPage = scene.page, initialTab = scene.tab, initialRoutes = scene.routes)
                } }
            }
        }
        val pages = listOf("bluetooth", "lora", "satellite", "chat", "routes", "qr", "contact", "settings", "box", "node", "language", "scan")
        val folder = File(context.getExternalFilesDir(null), "a4/${args.getString("viewport") ?: "screen"}").apply { mkdirs() }
        for (locale in listOf("en", "ru", "ro", "de", "fr")) for (dark in listOf(false, true)) for (page in pages) {
            val tab = when (page) { "lora" -> ChatTab.LORA; "satellite" -> ChatTab.SATELLITE; else -> ChatTab.BLUETOOTH }
            compose.runOnIdle { scene = Scene(locale, dark, if (page in listOf("bluetooth", "lora", "satellite")) "chats" else if (page == "routes") "chat" else page, tab, page == "routes") }
            compose.waitForIdle()
            val root = compose.onNodeWithTag("meshstar.unified")
            root.assertIsDisplayed()
            val bitmap = root.captureToImage().asAndroidBitmap()
            File(folder, "$locale-${if (dark) "dark" else "light"}-$page.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        // An additional small-screen, large-font check keeps manual route labels reachable.
        compose.runOnIdle { scene = Scene("ru", false, "chat", routes = true, scale = 1.3f) }
        compose.waitForIdle()
        compose.onNodeWithText("Интернет").assertIsDisplayed()
        compose.onNodeWithText("Релей").assertIsDisplayed()
        File(folder, "ru-light-routes-font130.png").outputStream().use { compose.onNodeWithTag("meshstar.unified").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}

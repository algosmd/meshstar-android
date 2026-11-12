package net.meshstar

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.bitchat.android.MainActivity
import com.bitchat.android.services.VerificationService
import net.meshstar.rnslite.Identity
import net.meshstar.rnslite.hex
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AppLinkInstrumentedTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun fullSignedLinkColdLaunchAndLegacyOnNewIntentUseScannerHandler() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val signed = signedLink()
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(signed)).setPackage(context.packageName)
            .addCategory(Intent.CATEGORY_BROWSABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        assertEquals(MainActivity::class.java.name, context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.name)
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            compose.waitUntil(20_000) { compose.onAllNodesWithTag("meshstar.verify.result").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("meshstar.verify.result").assertTextContains("Signature valid", substring = true)
            compose.onNodeWithTag("meshstar.link.input").assertTextContains(signed, substring = true)
            compose.onNodeWithText("Paste link").assertIsDisplayed()
            scenario.onActivity {
                val clipboard = it.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Synthetic link", signed))
            }
            compose.onNodeWithTag("meshstar.link.input").performTextClearance()
            compose.onNodeWithText("Paste link").performClick()
            compose.onNodeWithTag("meshstar.link.input").assertTextContains(signed, substring = true)
            compose.onNodeWithText("Open link").performClick()
            compose.onNodeWithTag("meshstar.verify.result").assertTextContains("Signature valid", substring = true)
            var original: MainActivity? = null
            scenario.onActivity { original = it }
            val legacy = signed.replace("meshstar:", "meshsat:")
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(legacy)).setPackage(context.packageName)
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText(legacy, substring = true).fetchSemanticsNodes().isNotEmpty()
            }
            scenario.onActivity { assertSame(original, it); assertEquals(legacy, it.intent.dataString) }
            compose.onNodeWithTag("meshstar.verify.result").assertTextContains("Signature valid", substring = true)
            // No peer radio exists on this fixture; URL self-signature is not mutual trust.
            compose.onNodeWithTag("meshstar.verify.result").assertTextContains("connect", substring = true)
            // Dismiss the modal before ActivityScenario teardown so its window lifecycle settles.
            compose.onNodeWithContentDescription(context.getString(com.bitchat.android.R.string.close_plain)).performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("meshstar.verify.result").fetchSemanticsNodes().isEmpty() }
            // ActivityScenario matches lifecycle callbacks against the original launch Intent.
            // onNewIntent correctly replaced Activity.intent; restore the fixture's launch
            // Intent only after checking that replacement, so teardown can track destruction.
            scenario.onActivity { it.intent = intent; it.finish() }
            // Keep Compose's frame clock running until the Activity finishes its exit transition.
            compose.waitUntil(10_000) { original?.isDestroyed == true }
        }
    }

    @Test fun allRequiredSchemesAndHostsResolveToMainActivity() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for (scheme in listOf("meshstar", "meshsat")) for (host in listOf("verify", "user", "share")) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("$scheme://$host")).setPackage(context.packageName)
                .addCategory(Intent.CATEGORY_BROWSABLE)
            assertEquals(MainActivity::class.java.name, context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.name)
        }
    }

    private fun signedLink(): String {
        // Fresh, synthetic key/timestamp. This is a complete correctly signed iOS-compatible payload.
        val identity = Identity(ByteArray(64) { (it + 33).toByte() })
        val payload = VerificationService.VerificationQR(1, identity.publicKey.copyOfRange(0,32).hex(),
            identity.publicKey.copyOfRange(32,64).hex(), null, "Synthetic", System.currentTimeMillis() / 1000,
            "AAAAAAAAAAAAAAAAAAAAAA", "")
        return payload.copy(sigHex = identity.sign(payload.canonicalBytes()).hex()).toUrlString()
    }
}

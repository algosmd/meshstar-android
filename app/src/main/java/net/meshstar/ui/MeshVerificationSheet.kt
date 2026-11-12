package net.meshstar.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bitchat.android.services.VerificationService
import com.bitchat.android.ui.ChatViewModel

/** Same verification service and challenge as the scanner, with MeshStar's non-glass chrome. */
@Composable fun MeshVerificationSheet(isPresented: Boolean, onDismiss: () -> Unit, viewModel: ChatViewModel, initialLink: String? = null) {
    if (!isPresented) return
    val base = LocalContext.current
    val prefs = remember { base.getSharedPreferences("meshstar.ui", android.content.Context.MODE_PRIVATE) }
    val localized = remember { localizedContext(base, prefs.getString("language", "").orEmpty()) }
    val dark = when (prefs.getString("appearance", "system")) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    var result by remember { mutableStateOf<T?>(null) }
    fun accept(value: String) {
        val qr = VerificationService.verifyScannedQR(value)
        result = when {
            qr == null -> T.verify_invalid
            viewModel.beginQRVerification(qr) -> T.verify_wait
            else -> T.verify_connect
        }
    }
    LaunchedEffect(initialLink) { initialLink?.let(::accept) }
    Dialog(onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        CompositionLocalProvider(LocalContext provides localized) { MeshTheme(dark) {
            Surface(Modifier.fillMaxSize().safeDrawingPadding().imePadding(), shape = MeshShape) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(tr(T.fingerprint), Modifier.weight(1f))
                        MeshIconButton(tr(T.close), Icons.Outlined.Close, onClick = onDismiss)
                    }
                    PasteLinkPanel(::accept, initialLink.orEmpty())
                    result?.let { Secondary(tr(it), Modifier.testTag("meshstar.verify.result")) }
                    Secondary(tr(T.compare), size = 10)
                }
            }
        } }
    }
}

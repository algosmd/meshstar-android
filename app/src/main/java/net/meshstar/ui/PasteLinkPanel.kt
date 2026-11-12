package net.meshstar.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** Clipboard is accessed only after an explicit tap, never on screen opening. */
@Composable
fun PasteLinkPanel(onOpen: (String) -> Unit, initialValue: String = "") {
    val context = LocalContext.current
    var value by remember { mutableStateOf(initialValue) }
    var error by remember { mutableStateOf(false) }
    LaunchedEffect(initialValue) { value = initialValue }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        CompactField(value, { if (it.length <= 4096) { value = it; error = false } else error = true },
            tr(T.link_text), Modifier.fillMaxWidth(), singleLine = false, tag = "meshstar.link.input")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MeshButton(tr(T.paste)) {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val text = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty().trim()
                if (text.length <= 4096) { value = text; error = false } else error = true
            }
            MeshButton(tr(T.open_link), enabled = value.isNotBlank()) { onOpen(value.trim()) }
        }
        if (error) Text(tr(T.link_long))
    }
}

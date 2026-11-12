package net.meshstar.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import com.bitchat.android.ui.theme.BitchatTheme
import net.meshstar.data.SatRuntime

class SatelliteActivity : ComponentActivity() {
    companion object {
        const val EXTRA_PEER = "net.meshstar.extra.PEER"
        const val EXTRA_DRAFT = "net.meshstar.extra.DRAFT"
    }
    private var initialPeer by mutableStateOf("")
    private var initialDraft by mutableStateOf("")
    private val runtime by lazy { SatRuntime.get(applicationContext) }
    private var incoming by mutableStateOf("")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyLink(intent)
        setContent { BitchatTheme { SatelliteScreen(runtime, incoming, { incoming = "" }, { finish() }, initialPeer, initialDraft) } }
    }
    private fun applyLink(intent: Intent) {
        incoming = intent.dataString.orEmpty().take(4096)
        initialPeer = intent.getStringExtra(EXTRA_PEER).orEmpty().take(128)
        initialDraft = intent.getStringExtra(EXTRA_DRAFT).orEmpty().take(4096)
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); applyLink(intent) }
    override fun onStart() { super.onStart(); runtime.attach(this) }
    override fun onStop() { runtime.detach(this); super.onStop() }
}

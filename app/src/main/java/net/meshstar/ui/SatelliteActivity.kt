package net.meshstar.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import com.bitchat.android.ui.theme.BitchatTheme
import net.meshstar.data.SatRuntime

class SatelliteActivity : ComponentActivity() {
    private val runtime by lazy { SatRuntime.get(applicationContext) }
    private var incoming by mutableStateOf("")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        incoming = intent.dataString.orEmpty().take(4096)
        setContent { BitchatTheme { SatelliteScreen(runtime, incoming, { incoming = "" }, { finish() }) } }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); incoming = intent.dataString.orEmpty().take(4096) }
    override fun onStart() { super.onStart(); runtime.attach(this) }
    override fun onStop() { runtime.detach(this); super.onStop() }
}

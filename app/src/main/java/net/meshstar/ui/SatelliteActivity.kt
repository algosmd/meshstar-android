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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        forward(intent)
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); forward(intent) }
    private fun forward(source: Intent) {
        startActivity(Intent(this, com.bitchat.android.MainActivity::class.java)
            .setAction(source.action).setData(source.data)
            .putExtra(EXTRA_PEER, source.getStringExtra(EXTRA_PEER))
            .putExtra(EXTRA_DRAFT, source.getStringExtra(EXTRA_DRAFT))
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        finish()
    }
}

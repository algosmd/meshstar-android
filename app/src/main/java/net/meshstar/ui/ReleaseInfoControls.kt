package net.meshstar.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import net.meshstar.notifications.IncomingNotifications
import java.io.File

@Composable fun NotificationControls() {
    val context=LocalContext.current
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    MeshCard {
        if(Build.VERSION.SDK_INT>=33) MeshButton(tr(T.notification_allow)) { permission.launch(Manifest.permission.POST_NOTIFICATIONS) }
        MeshButton(tr(T.notification_settings)) {
            context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName))
        }
    }
}

@Composable fun CodecLicenseControls() {
    val context=LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    MeshCard {
        MeshButton(tr(T.licenses)) { expanded=!expanded }
        if(expanded) {
            Secondary("Codec2 1.2.0 · LGPL-2.1 · KISS FFT BSD",size=10)
            Secondary(remember { context.assets.open("codec2-licenses.txt").bufferedReader().use{it.readText()} },size=10)
            MeshButton(tr(T.share)) {
                runCatching {
                    val file=File(context.cacheDir,"codec2-source.zip")
                    context.assets.open("codec2-source.zip").use { input -> file.outputStream().use { input.copyTo(it) } }
                    val uri=FileProvider.getUriForFile(context,context.packageName+".fileprovider",file)
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("application/zip")
                        .putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),null))
                }.onFailure { failed=true }
            }
        }
        if(failed)Secondary(tr(T.voice_error),size=10)
    }
}

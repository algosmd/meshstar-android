package net.meshstar.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import net.meshstar.voice.VoiceAudio
import kotlinx.coroutines.launch

@Composable fun rememberVoiceAudio(): VoiceAudio {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val audio = remember { VoiceAudio(scope, context) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) audio.cancel() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); audio.cancel() }
    }
    return audio
}

@Composable fun VoiceRecordButton(audio: VoiceAudio, enabled: Boolean) {
    val context = LocalContext.current
    val recording by audio.recording.collectAsState()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { /* A fresh hold starts recording. */ }
    fun begin() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            permission.launch(Manifest.permission.RECORD_AUDIO)
        else audio.begin()
    }
    val label = tr(T.voice_record)
    Box(Modifier.size(44.dp).semantics {
        contentDescription = label; role = Role.Button
        onClick { if (enabled) { if (recording) audio.finish() else begin() }; enabled }
    }.pointerInput(enabled) {
        if (enabled) detectTapGestures(onPress = {
            begin()
            val released = tryAwaitRelease()
            if (released) audio.finish() else audio.cancel()
        })
    }, contentAlignment = Alignment.Center) {
        val color = if (recording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        Box(Modifier.size(22.dp).background(color.copy(alpha = if (enabled) .1f else .03f), RoundedCornerShape(3.dp))
            .border(1.dp, color.copy(alpha = if (enabled) .4f else .1f), RoundedCornerShape(3.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Mic, null, Modifier.size(16.dp), tint = color.copy(alpha = if (enabled) 1f else .3f))
        }
    }
}

@Composable fun VoicePreview(audio: VoiceAudio, usesLoRa: Boolean, enabled: Boolean, send: suspend (ByteArray) -> Boolean) {
    val recording by audio.recording.collectAsState()
    val remaining by audio.remaining.collectAsState()
    val draft by audio.draft.collectAsState()
    val failed by audio.failed.collectAsState()
    val playing by audio.playing.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sending by remember { mutableStateOf(false) }
    Secondary(tr(T.voice_compressed), size = 10)
    if (recording) Secondary(context.getString(T.voice_remaining.resource, remaining), size = 12)
    draft?.let { bytes ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            MeshIconButton(tr(if (playing) T.voice_stop else T.voice_play), if (playing) Icons.Outlined.Stop else Icons.Outlined.PlayArrow, composer = true) { audio.play(bytes) }
            Secondary(context.getString(T.voice_duration.resource, bytes.size / 150.0), Modifier.weight(1f))
            MeshButton(tr(T.voice_delete), enabled = !sending) { audio.cancel() }
            MeshButton(tr(T.send), enabled = enabled && !sending) {
                scope.launch { sending = true; try { if (send(bytes)) audio.clearDraft() } finally { sending = false } }
            }
        }
        if (usesLoRa) {
            Secondary(tr(T.voice_lora_warning), size = 10)
        }
    }
    if (draft == null && !recording) Secondary(tr(T.voice_hold), size = 10)
    if (failed) Secondary(tr(T.voice_error), size = 10)
}

@Composable fun VoicePlayback(codec: ByteArray) {
    val audio = rememberVoiceAudio()
    val playing by audio.playing.collectAsState()
    val failed by audio.failed.collectAsState()
    Row(verticalAlignment = Alignment.CenterVertically) {
        MeshIconButton(tr(if (playing) T.voice_stop else T.voice_play), if (playing) Icons.Outlined.Stop else Icons.Outlined.PlayArrow, composer = true) { audio.play(codec) }
        Secondary(LocalContext.current.getString(T.voice_duration.resource, codec.size / 150.0))
    }
    if (failed) Secondary(tr(T.voice_error), size = 10)
}

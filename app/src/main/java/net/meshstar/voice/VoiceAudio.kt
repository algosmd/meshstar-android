package net.meshstar.voice

import android.media.*
import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import net.meshstar.codec2.Codec2
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

/** PCM stays in memory. Capture stops on release, cancellation, background or the 31.92 s limit. */
class VoiceAudio(private val scope: CoroutineScope, context: Context) {
    private val manager = context.applicationContext.getSystemService(AudioManager::class.java)
    private val generation = AtomicInteger()
    companion object {
        private val active = AtomicReference<VoiceAudio?>(null)
        private val output = Mutex()
    }
    val recording = MutableStateFlow(false)
    val remaining = MutableStateFlow(Codec2.MAX_SAMPLES / 8000.0)
    val draft = MutableStateFlow<ByteArray?>(null)
    val failed = MutableStateFlow(false)
    val playing = MutableStateFlow(false)
    private var capture: Job? = null
    private var playback: Job? = null
    private val finish = AtomicBoolean(false)
    private val discard = AtomicBoolean(false)
    fun begin() {
        if (capture?.isActive == true || draft.value != null) return
        active.getAndSet(null)?.stopPlayback(); stopPlayback(); failed.value = false; finish.set(false); discard.set(false)
        recording.value = true
        capture = scope.launch(Dispatchers.IO) {
            var recorder: AudioRecord? = null
            val pcm = ShortArray(Codec2.MAX_SAMPLES)
            try {
                val minimum = AudioRecord.getMinBufferSize(8000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                check(minimum > 0)
                recorder = AudioRecord(MediaRecorder.AudioSource.MIC, 8000, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, maxOf(minimum, 3200))
                check(recorder.state == AudioRecord.STATE_INITIALIZED)
                recorder.startRecording()
                check(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING)
                var count = 0
                while (isActive && !finish.get() && count < pcm.size) {
                    val n = recorder.read(pcm, count, minOf(320, pcm.size - count), AudioRecord.READ_BLOCKING)
                    check(n > 0); count += n
                    remaining.value = (pcm.size - count) / 8000.0
                }
                recorder.stop(); recorder.release(); recorder = null
                if (!discard.get() && isActive) {
                    check(count >= 320)
                    val samples = pcm.copyOf(count)
                    try { draft.value = withContext(Dispatchers.Default) { Codec2.encode(samples) } }
                    finally { samples.fill(0) }
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { if (!discard.get()) failed.value = true }
            finally { runCatching { recorder?.stop() }; recorder?.release(); pcm.fill(0); recording.value = false }
        }
    }
    fun finish() { finish.set(true) }
    fun cancel() { discard.set(true); finish.set(true); capture?.cancel(); draft.value = null; stopPlayback() }
    fun clearDraft() { draft.value = null }
    fun play(bytes: ByteArray) {
        if (playing.value) { stopPlayback(); return }
        active.getAndSet(this)?.stopPlayback()
        val attempt = generation.incrementAndGet()
        playing.value = true; failed.value = false
        playback = scope.launch(Dispatchers.IO) { output.withLock {
            var track: AudioTrack? = null
            var pcm: ShortArray? = null
            val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
            val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attributes).setOnAudioFocusChangeListener { change ->
                    if (change < 0) stopPlayback()
                }.build()
            try {
                check(manager.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
                pcm = Codec2.decode(bytes)
                val format = AudioFormat.Builder().setSampleRate(8000).setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
                track = AudioTrack.Builder().setAudioAttributes(attributes)
                    .setAudioFormat(format).setTransferMode(AudioTrack.MODE_STREAM)
                    .setBufferSizeInBytes(maxOf(3200, AudioTrack.getMinBufferSize(8000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT))).build()
                track.play()
                var at = 0
                while (isActive && at < pcm.size) {
                    val n = track.write(pcm, at, minOf(320, pcm.size - at), AudioTrack.WRITE_BLOCKING)
                    check(n > 0); at += n
                }
                while (isActive && track.playbackHeadPosition < pcm.size) delay(20)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { if (attempt == generation.get()) failed.value = true }
            finally {
                runCatching { track?.stop() }; track?.release(); pcm?.fill(0)
                manager.abandonAudioFocusRequest(focus)
                if (attempt == generation.get()) { playing.value = false; active.compareAndSet(this@VoiceAudio, null) }
            }
        } }
    }
    fun stopPlayback() { generation.incrementAndGet(); playback?.cancel(); playing.value = false; active.compareAndSet(this, null) }
}

object VoiceWav {
    fun encode(pcm: ShortArray): ByteArray {
        require(pcm.size in 1..Codec2.MAX_SAMPLES)
        val buffer = ByteBuffer.allocate(44 + pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray()).putInt(36 + pcm.size * 2).put("WAVEfmt ".toByteArray())
        buffer.putInt(16).putShort(1).putShort(1).putInt(8000).putInt(16000).putShort(2).putShort(16)
        buffer.put("data".toByteArray()).putInt(pcm.size * 2)
        pcm.forEach { buffer.putShort(it) }
        return buffer.array()
    }
}

package net.meshstar.data

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest
import net.meshstar.box.HttpFailure
import net.meshstar.identity.EncryptedFiles
import net.meshstar.rnslite.RNSError
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.GeneralSecurityException

/** One process owner shared by the mesh foreground service and visible Satellite UI.
 * Activity fallback makes Internet usable on an emulator without BLE permissions. */
class SatRuntime private constructor(context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val engineValue = MutableStateFlow<SatEngine?>(null)
    val engine = engineValue.asStateFlow()
    val startupError = MutableStateFlow<String?>(null)
    val boxStatus = MutableStateFlow("Box not configured")
    val mailStatus = MutableStateFlow("Internet server disabled")
    private val owners = mutableSetOf<Any>()
    private var work: Job? = null
    @Volatile private var erased = false
    private val initialized = scope.async {
        try {
            val files = EncryptedFiles(context.applicationContext)
            SatEngine(files.identity(), EncryptedStateStore(files)).also { engineValue.value = it }
        } catch (e: Exception) { startupError.value = "Keystore / storage unavailable. Identity was not replaced."; throw e }
    }
    @Synchronized fun attach(owner: Any) {
        if (erased) return
        owners.add(owner)
        if (work?.isActive == true) return
        work = scope.launch {
            val engine = try { initialized.await() } catch (_: Exception) { return@launch }
            engine.state.map { it.box to it.internet }.distinctUntilChanged().collectLatest {
              supervisorScope {
                launch { loop(boxStatus, 5_000) {
                    if (engine.state.value.box == null) { boxStatus.value = "Box not configured"; return@loop }
                    engine.flushOutgoing(Route.SATELLITE); engine.pollBox(); boxStatus.value = "Box available"
                } }
                launch { loop(mailStatus, 2_000) { engine.flushOutgoing(Route.INTERNET) } }
                launch { loop(mailStatus, 500) {
                    if (engine.state.value.internet.isEmpty()) { mailStatus.value = "Internet server disabled"; delay(2_000); return@loop }
                    if (engine.state.value.contacts.isEmpty()) { mailStatus.value = "Add a contact to receive"; delay(2_000); return@loop }
                    engine.pollMailbox(25); mailStatus.value = "Internet server available"
                } }
              }
            }
        }
    }
    @Synchronized fun detach(owner: Any) {
        owners.remove(owner)
        if (owners.isEmpty()) { work?.cancel(); work = null }
    }
    private suspend fun loop(status: MutableStateFlow<String>, interval: Long, action: suspend () -> Unit) {
        var failures = 0
        while (currentCoroutineContext().isActive) {
            var retry = interval
            try { action(); failures = 0 }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                failures = minOf(failures + 1, 6)
                status.value = safeFailure(e)
                retry = maxOf(interval, if (e is HttpFailure) e.retrySeconds * 1000 else minOf(60_000, 1000L shl failures))
            }
            delay(retry)
        }
    }
    companion object {
        @Volatile private var instance: SatRuntime? = null
        fun get(context: Context): SatRuntime = instance ?: synchronized(this) { instance ?: SatRuntime(context).also { instance = it } }
        suspend fun erase(context: Context) = withContext(Dispatchers.IO) {
            val runtime = instance
            if (runtime != null) {
                synchronized(runtime) { runtime.erased = true; runtime.owners.clear() }
                runtime.work?.cancelAndJoin()
                runtime.initialized.cancelAndJoin()
                runtime.engineValue.value?.closeForReset()
                runtime.engineValue.value = null
                runtime.startupError.value = "Data erased. Restart the application for a new RNS identity."
            }
            EncryptedFiles(context.applicationContext).erase()
        }
    }
}

fun safeFailure(error: Throwable): String = when (error) {
    is HttpFailure -> when (error.code) {
        401 -> "HTTP 401: check the box token"
        403 -> "HTTP 403: access denied"
        429 -> "HTTP 429: retrying after the server limit"
        507 -> "HTTP 507: server storage is full"
        in 300..399 -> "HTTP ${error.code}: redirects are disabled; enter the final URL"
        else -> "HTTP ${error.code}"
    }
    is SocketTimeoutException -> "Network timeout: check connection, URL and VPN"
    is UnknownHostException -> "DNS error: server name could not be resolved"
    is GeneralSecurityException -> "Keystore / encrypted storage error"
    is IOException -> "Network or storage I/O error"
    is RNSError -> "RNS: ${error.message}"
    is IllegalArgumentException -> "Invalid input or server response"
    else -> "Operation failed; check settings and try again"
}

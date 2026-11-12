package net.meshstar.ui

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import com.bitchat.android.ui.ChatViewModel
import com.bitchat.android.model.BitchatMessage
import com.bitchat.android.model.DeliveryStatus
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.meshstar.data.*
import net.meshstar.link.ContactLink
import java.util.Locale

@Composable
fun SatelliteScreen(runtime: SatRuntime, incoming: String, consumeLink: () -> Unit, close: () -> Unit,
                    initialPeer: String = "", initialDraft: String = "", bluetooth: ChatViewModel? = null,
                    bluetoothSetup: () -> Unit = {}, bleReady: Boolean = false) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("meshstar.ui", Context.MODE_PRIVATE) }
    var language by remember { mutableStateOf(prefs.getString("language", "").orEmpty()) }
    var appearance by remember { mutableStateOf(prefs.getString("appearance", "system").orEmpty()) }
    val configuration = LocalConfiguration.current
    val localized = remember(language, configuration) {
        localizedContext(context, language)
    }
    val dark = when (appearance) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    val engine by runtime.engine.collectAsState()
    val startupError by runtime.startupError.collectAsState()
    DisposableEffect(runtime) { val owner = Any(); runtime.attach(owner); onDispose { runtime.detach(owner) } }
    CompositionLocalProvider(LocalContext provides localized) {
        MeshTheme(dark) {
            if (engine == null) androidx.compose.material3.Text(tr(if (startupError == null) T.opening else T.storage_error))
            else ConnectedScreen(engine!!, runtime, incoming, consumeLink, initialPeer, initialDraft, bluetooth, bleReady,
                language, appearance, onPreferences = { action ->
                    when (action) {
                        is UiAction.Language -> { language = action.value; prefs.edit().putString("language", language).apply() }
                        is UiAction.Appearance -> { appearance = action.value; prefs.edit().putString("appearance", appearance).apply() }
                        UiAction.BluetoothSetup -> bluetoothSetup()
                        else -> Unit
                    }
                })
        }
    }
}

@Composable private fun ConnectedScreen(engine: SatEngine, runtime: SatRuntime, incoming: String, consumeLink: () -> Unit,
    initialPeer: String, initialDraft: String, ble: ChatViewModel?, bleReady: Boolean, language: String, appearance: String,
    onPreferences: (UiAction) -> Unit) {
    val state by engine.state.collectAsState()
    val point by engine.pointOnline.collectAsState()
    val boxStatus by runtime.boxStatus.collectAsState()
    val mailStatus by runtime.mailStatus.collectAsState()
    val bleNickname = ble?.nickname?.collectAsState()?.value
    val nickname = state.name.takeUnless { it == "MeshStar" } ?: bleNickname ?: state.name
    val connected = ble?.connectedPeers?.collectAsState()?.value.orEmpty()
    val names = ble?.peerNicknames?.collectAsState()?.value.orEmpty()
    val fingerprints = ble?.peerFingerprints?.collectAsState()?.value.orEmpty()
    val verified = ble?.verifiedFingerprints?.collectAsState()?.value.orEmpty()
    val privateChats = ble?.privateChats?.collectAsState()?.value.orEmpty()
    val unreadBle = ble?.unreadPrivateMessages?.collectAsState()?.value.orEmpty()
    val publicMessages = ble?.messages?.collectAsState()?.value.orEmpty()
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("meshstar.ui", Context.MODE_PRIVATE) }
    var publicRead by remember { mutableLongStateOf(prefs.getLong("publicRead", System.currentTimeMillis())) }
    val histories = remember(state.messages) { state.messages.groupBy { it.peer } }
    val contactFingerprints = remember(state.contacts) { state.contacts.associate { it.address to it.fingerprint } }
    val ownContact = remember(engine.identity, nickname) { net.meshstar.link.Contact.of(engine.identity, nickname) }
    val rns = state.contacts.map { c ->
        val history = histories[c.address].orEmpty()
        UiPeer("rns:${c.address}", c.name, contactFingerprints.getValue(c.address), rns = c.address,
            verified = state.verifiedKeys[c.address] == c.key, preview = history.lastOrNull()?.text.orEmpty(),
            unread = history.count { !it.outgoing && it.at > (state.readThrough[c.address] ?: 0) },
            paths = setOf(ChatTab.SATELLITE), at = history.lastOrNull()?.at ?: 0, qr = c.qr)
    } + state.messages.map { it.peer }.distinct().filter { p -> state.contacts.none { it.address == p } }.map { p ->
        val history = histories[p].orEmpty()
        UiPeer("rns:$p", p.take(12), rns = p, preview = history.lastOrNull()?.text.orEmpty(),
            unread = history.count { !it.outgoing && it.at > (state.readThrough[p] ?: 0) }, paths = setOf(ChatTab.SATELLITE), at = history.lastOrNull()?.at ?: 0)
    }
    val blePeers = (connected + privateChats.keys).distinct().filterNot { it.startsWith("nostr_") }.map { peer ->
        val fp = fingerprints[peer] ?: ble?.getPeerFingerprintForDisplay(peer).orEmpty()
        val history = privateChats[peer].orEmpty()
        UiPeer("ble:$peer", names[peer] ?: peer.take(12), fp, ble = peer, bleFingerprint = fp.takeIf { it.isNotEmpty() },
            nearby = peer in connected, verified = fp in verified, preview = history.lastOrNull()?.content.orEmpty(),
            unread = if (peer in unreadBle) 1 else 0, paths = setOf(ChatTab.BLUETOOTH), at = history.lastOrNull()?.timestamp?.time ?: 0)
    }
    val peers = unifiedPeers(rns, blePeers, state.bluetoothLinks)
    fun bleMessage(m: BitchatMessage): UiMessage = UiMessage(m.id, m.sender, m.content, m.timestamp.time,
        m.senderPeerID == ble?.myPeerID || m.sender == bleNickname, delivery = when (m.deliveryStatus) {
            is DeliveryStatus.Delivered, is DeliveryStatus.Read -> Delivery.DELIVERED
            is DeliveryStatus.Sending -> Delivery.QUEUED
            else -> Delivery.SENT
        }, peer = peers.firstOrNull { it.ble == m.senderPeerID }?.id ?: "ble:${m.senderPeerID ?: m.sender}", showDelivery = m.isPrivate && m.deliveryStatus != null)
    val timelines = peers.associate { peer -> peer.id to (
        histories[peer.rns].orEmpty().map { m -> UiMessage(m.hash, if (m.outgoing) nickname else peer.name,
            m.text, m.at, m.outgoing, m.route, m.viaPoint, m.delivery,
            if (m.outgoing) { if (m.delivery == Delivery.DELIVERED) true else null } else m.verified, peer.id) } +
            peer.ble?.let { privateChats[it]?.map(::bleMessage) }.orEmpty()).sortedBy { it.at } }
    val snapshot = UiSnapshot(nickname, peers, timelines, publicMessages.map(::bleMessage),
        publicMessages.count { it.timestamp.time > publicRead && it.senderPeerID != ble?.myPeerID && it.sender != nickname },
        ownContact.qr, ownContact.fingerprint, ownContact.address,
        state.box?.url.orEmpty(), state.box?.token.orEmpty(), state.internet, point, boxStatus, mailStatus, bleReady,
        mapOf(ChatTab.BLUETOOTH to (blePeers.sumOf { it.unread } + publicMessages.count { it.timestamp.time > publicRead && it.senderPeerID != ble?.myPeerID && it.sender != bleNickname }),
            ChatTab.LORA to 0, ChatTab.SATELLITE to rns.sumOf { it.unread }))
    val scope = rememberCoroutineScope()
    var feedback by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var openedPeer by remember { mutableStateOf(initialPeer.takeIf { it.isNotEmpty() }?.let { if (it.contains(":")) it else "rns:$it" }.orEmpty()) }
    var pendingImport by remember { mutableStateOf("") }
    var pendingBox by remember { mutableStateOf<Pair<String,String>?>(null) }
    val currentState by rememberUpdatedState(state)
    suspend fun action(event: UiAction): Boolean {
        feedback = null
        try {
            when (event) {
                is UiAction.Send -> {
                    val peer = peers.firstOrNull { it.id == event.peer }
                    if (event.peer == "public" || (event.path == PathChoice.AUTO && peer?.rns == null)) {
                        check(ble != null && bleReady)
                        if (event.peer == "public") { ble.endPrivateChat(); ble.selectLocationChannel(com.bitchat.android.geohash.ChannelID.Mesh); ble.switchToChannel(null) }
                        else { ble.startPrivateChat(peer?.ble ?: error("Unknown peer")) }
                        return kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
                            ble.sendMessage(event.text) { accepted -> if (continuation.isActive) continuation.resume(accepted, null) }
                        }
                    }
                    val choice = resolvePath(event.path, peer?.nearby == true && bleReady, currentState.internet.isNotEmpty(), currentState.box != null)
                    if (choice == null) { feedback = context.getString(T.route_unavailable.resource); return false }
                    if (choice == "bluetooth") {
                        check(ble != null); ble.startPrivateChat(peer?.ble ?: error("Unknown peer"))
                        return kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
                            ble.sendMessage(event.text) { accepted -> if (continuation.isActive) continuation.resume(accepted, null) }
                        }
                    }
                    withContext(Dispatchers.IO) { engine.send(peer?.rns ?: error("Add RNS contact"), event.text, if (choice == "internet") Route.INTERNET else Route.SATELLITE) }
                }
                is UiAction.Read -> {
                    if (event.peer == "public") { publicRead = System.currentTimeMillis(); prefs.edit().putLong("publicRead", publicRead).apply(); ble?.clearMeshMentionNotifications() }
                    peers.firstOrNull { it.id == event.peer }?.let { peer ->
                        if (peer.rns != null && peer.unread > 0) withContext(Dispatchers.IO) { engine.markRead(peer.rns) }
                        if (peer.ble != null && ble?.selectedPrivateChatPeer?.value != peer.ble) ble?.startPrivateChat(peer.ble)
                    }
                }
                is UiAction.Import -> {
                    val uri = runCatching { android.net.Uri.parse(event.text) }.getOrNull()
                    if (uri != null && net.meshstar.link.AppLinks.navigation(uri)) {
                        context.startActivity(Intent(context, com.bitchat.android.MainActivity::class.java).setAction(Intent.ACTION_VIEW).setData(uri)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                    } else {
                        val box = runCatching { ContactLink.box(event.text) }.getOrNull()
                        if (box != null) { pendingBox = box; feedback = context.getString(T.review_box.resource) }
                        else { val contact = withContext(Dispatchers.IO) { engine.addContact(event.text) }; openedPeer = "rns:${contact.address}" }
                    }
                    pendingImport = ""
                }
                is UiAction.Verify -> peers.firstOrNull { it.id == event.peer }?.let { peer ->
                    if (peer.rns != null) withContext(Dispatchers.IO) { engine.verifyContact(peer.rns, event.value) }
                    else peer.bleFingerprint?.let { if (event.value) ble?.verifyFingerprintValue(it) else ble?.unverifyFingerprintValue(it) }
                }
                is UiAction.Link -> {
                    val peer = peers.first { it.id == event.blePeer }; val target = peers.first { it.id == event.rnsPeer }
                    withContext(Dispatchers.IO) { engine.linkBluetooth(peer.bleFingerprint ?: error("Unknown key"), target.rns ?: error("Unknown contact")) }
                    openedPeer = target.id
                }
                is UiAction.SaveBox -> { busy = true; withContext(Dispatchers.IO) { engine.configureBox(event.url, event.token) }; feedback = context.getString(T.paired.resource) }
                is UiAction.SaveInternet -> withContext(Dispatchers.IO) { engine.configureInternet(event.url) }
                UiAction.DisableBox -> withContext(Dispatchers.IO) { engine.disableBox() }
                is UiAction.Rename -> { withContext(Dispatchers.IO) { engine.setName(event.name) }; ble?.setNickname(event.name) }
                else -> onPreferences(event)
            }
            return true
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { feedback = localizedFailure(context, safeFailure(e)); return false }
        finally { busy = false }
    }
    LaunchedEffect(initialPeer) { if (initialPeer.isNotEmpty()) openedPeer = if (initialPeer.contains(":")) initialPeer else "rns:$initialPeer" }
    LaunchedEffect(incoming) { if (incoming.isNotEmpty()) { pendingImport = incoming; consumeLink() } }
    UnifiedScreen(snapshot, ::action, language, appearance, feedback, busy, openedPeer, initialDraft, pendingImport, pendingBox)
}

fun localizedFailure(context: Context, status: String): String {
    val key = when {
        status == "Box not configured" -> T.box_off
        status == "Box available" -> T.box_ready
        status == "Internet server disabled" -> T.mail_off
        status == "Internet server available" -> T.mail_ready
        status == "Add a contact to receive" -> T.need_contact
        status.startsWith("HTTP 401") -> T.http_401
        status.startsWith("HTTP 403") -> T.http_403
        status.startsWith("HTTP 429") -> T.http_429
        status.startsWith("HTTP 507") -> T.http_507
        status.startsWith("HTTP 3") -> T.http_redirect
        status.startsWith("HTTP ") -> return status.take(8)
        status.startsWith("Network timeout") -> T.timeout
        status.startsWith("DNS") -> T.dns_error
        status.startsWith("Keystore") -> T.storage_error
        status.startsWith("Network or storage") -> T.network_error
        status.startsWith("RNS:") || status.startsWith("Invalid") -> T.invalid
        else -> T.failed
    }
    return context.getString(key.resource)
}
internal fun contactQrBitmap(text: String): Bitmap {
    val bits = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 600, 600, mapOf(com.google.zxing.EncodeHintType.CHARACTER_SET to "UTF-8"))
    val pixels = IntArray(bits.width * bits.height) { i -> if (bits[i % bits.width, i / bits.width]) android.graphics.Color.BLACK else android.graphics.Color.WHITE }
    return Bitmap.createBitmap(pixels, bits.width, bits.height, Bitmap.Config.ARGB_8888)
}

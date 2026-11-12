package net.meshstar.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.meshstar.data.*
import net.meshstar.link.ContactLink

@Composable
fun MeshButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(onClick, modifier.heightIn(min = 44.dp), enabled = enabled,
        shape = RoundedCornerShape(3.dp), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 3.dp)) {
        Text(text, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
    }
}

@Composable
fun SatelliteScreen(runtime: SatRuntime, incoming: String, consumeLink: () -> Unit, close: () -> Unit, initialPeer: String = "", initialDraft: String = "") {
    val engine by runtime.engine.collectAsState()
    val startupError by runtime.startupError.collectAsState()
    Surface(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
        if (engine == null) Column(Modifier.padding(12.dp)) { Text(startupError ?: "Opening encrypted storage…"); MeshButton("Back", onClick = close) }
        else Content(engine!!, runtime, incoming, consumeLink, close, initialPeer, initialDraft)
    }
}

@Composable
private fun Content(engine: SatEngine, runtime: SatRuntime, incoming: String, consumeLink: () -> Unit, close: () -> Unit, initialPeer: String, initialDraft: String) {
    val state by engine.state.collectAsState()
    val boxStatus by runtime.boxStatus.collectAsState()
    val mailStatus by runtime.mailStatus.collectAsState()
    var screen by remember { mutableStateOf("chats") }
    var selected by remember { mutableStateOf("") }
    var route by remember { mutableStateOf(Route.SATELLITE) }
    var input by remember { mutableStateOf("") }
    var qrInput by remember { mutableStateOf("") }
    var boxUrl by remember { mutableStateOf(state.box?.url.orEmpty()) }
    var token by remember { mutableStateOf(state.box?.token.orEmpty()) }
    var internet by remember { mutableStateOf(state.internet) }
    var name by remember { mutableStateOf(state.name) }
    var busy by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    fun run(action: suspend () -> Unit) {
        if (busy) return
        busy = true; feedback = null
        scope.launch {
            try { withContext(Dispatchers.IO) { action() } }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { feedback = safeFailure(e) }
            finally { busy = false }
        }
    }
    fun copy(text: String) {
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("MeshStar contact", text))
        feedback = "Copied"
    }
    fun scanResult(text: String) {
        val uri = runCatching { android.net.Uri.parse(text) }.getOrNull()
        if (uri != null && net.meshstar.link.AppLinks.navigation(uri) && net.meshstar.link.AppLinks.safe(uri)) {
            context.startActivity(android.content.Intent(context, com.bitchat.android.MainActivity::class.java)
                .setAction(android.content.Intent.ACTION_VIEW).setData(uri)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP))
            return
        }
        val box = runCatching { ContactLink.box(text) }.getOrNull()
        if (box != null) { boxUrl = box.first; token = box.second; screen = "settings"; feedback = "Box QR imported. Review and tap Save and check." }
        else { qrInput = text; screen = "add" }
    }
    LaunchedEffect(initialPeer) { if (initialPeer.isNotBlank()) { selected = initialPeer; screen = "chat" } }
    LaunchedEffect(initialDraft) { if (initialDraft.isNotEmpty()) { input = initialDraft; feedback = "Select a contact to send the shared draft" } }
    LaunchedEffect(incoming) { if (incoming.isNotEmpty()) { scanResult(incoming); consumeLink() } }
    androidx.activity.compose.BackHandler(screen != "chats") { screen = "chats" }
    CompositionLocalProvider(LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 14.sp)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MeshButton("Back") { if (screen == "chats") close() else screen = "chats" }
                Text("MeshStar", Modifier.weight(1f).padding(top = 12.dp))
                MeshButton("My QR") { screen = "qr" }
                MeshButton("Settings") { screen = "settings" }
            }
            feedback?.let { Text(it, Modifier.padding(vertical = 6.dp), fontSize = 12.sp) }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            when (screen) {
                "scan" -> ContactScanner(::scanResult)
                "qr" -> Column(Modifier.verticalScroll(rememberScrollState()).weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("My RNS contact")
                    Field(name, { name = it }, "Name")
                    MeshButton("Save name", enabled = !busy) { run { engine.setName(name) } }
                    val qr = engine.myContact().qr
                    val bitmap = remember(qr) { contactQrBitmap(qr) }
                    Image(bitmap.asImageBitmap(), "My contact QR", Modifier.fillMaxWidth().heightIn(max = 280.dp).aspectRatio(1f))
                    Text(engine.identity.lxmfAddress.joinToString("") { "%02x".format(it) }, fontSize = 12.sp)
                    Text("Fingerprint: ${engine.myContact().fingerprint}", fontSize = 12.sp)
                    Text("RNS contact QR is compatible with iOS and boxes.", fontSize = 12.sp)
                    MeshButton("Copy contact for iOS / box") { copy(qr) }
                    MeshButton("Copy MeshStar link") { copy(engine.myContact().link) }
                }
                "add" -> Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Add contact")
                    Field(qrInput, { qrInput = it }, "QR text or MeshStar contact link", singleLine = false)
                    MeshButton("Scan QR") { screen = "scan" }
                    MeshButton("Add contact", enabled = !busy) { run {
                        val contact = engine.addContact(qrInput)
                        withContext(Dispatchers.Main) { selected = contact.address; screen = "chat"; qrInput = "" }
                    } }
                }
                "settings" -> Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Box settings")
                    Text(boxStatus, fontSize = 12.sp)
                    Field(boxUrl, { boxUrl = it }, "Box URL")
                    OutlinedTextField(token, { token = it }, Modifier.fillMaxWidth(), label = { Text("Box token") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(), shape = RoundedCornerShape(3.dp))
                    MeshButton("Scan box setup QR") { screen = "scan" }
                    MeshButton("Save and check", enabled = !busy) { run {
                        engine.configureBox(boxUrl, token)
                        withContext(Dispatchers.Main) { feedback = "Box paired ✓" }
                    } }
                    MeshButton("Disable box", enabled = !busy) { run { engine.disableBox(); withContext(Dispatchers.Main) { token = ""; boxUrl = "" } } }
                    HorizontalDivider()
                    Text("Internet server")
                    Text(mailStatus, fontSize = 12.sp)
                    Field(internet, { internet = it }, "Internet server URL")
                    Text("Empty = disabled. Polling runs while this screen or the mesh foreground service is active.", fontSize = 12.sp)
                    MeshButton("Save internet server", enabled = !busy) { run {
                        engine.configureInternet(internet)
                        withContext(Dispatchers.Main) { feedback = "Saved" }
                    } }
                }
                "chat" -> {
                    val contact = state.contacts.firstOrNull { it.address == selected }
                    Text(contact?.name ?: selected.take(12), Modifier.padding(vertical = 8.dp))
                    Text(contact?.let { "Fingerprint: ${it.fingerprint}" } ?: "Unknown sender · add QR to verify future messages", fontSize = 12.sp)
                    LazyColumn(Modifier.weight(1f), reverseLayout = true, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(state.messages.filter { it.peer == selected }.asReversed(), key = { it.hash }) { message ->
                            Column {
                                Text("<${if (message.outgoing) state.name else contact?.name ?: "unknown"}> ${message.text}")
                                Text((if (message.route == Route.INTERNET) "🌐 Internet" else "🛰 Satellite") + " · " +
                                    (if (message.outgoing) when (message.delivery) {
                                        Delivery.QUEUED -> "queued"
                                        Delivery.SENT -> "sent · waiting for confirmation"
                                        Delivery.DELIVERED -> "✅ delivered"
                                    } else if (message.verified) "signature OK ✓" else "signature not verified ✗"), fontSize = 10.sp)
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        MeshButton(if (route == Route.SATELLITE) "✓ Satellite" else "Satellite") { route = Route.SATELLITE }
                        MeshButton(if (route == Route.INTERNET) "✓ Internet" else "Internet") { route = Route.INTERNET }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(input, { input = it }, Modifier.weight(1f), placeholder = { Text("Message") }, maxLines = 4, shape = RoundedCornerShape(3.dp))
                        MeshButton("Send", enabled = !busy && input.isNotBlank() && contact != null) { run {
                            engine.send(selected, input, route)
                            withContext(Dispatchers.Main) { input = "" }
                        } }
                    }
                }
                else -> {
                    Text("Satellite / Internet", Modifier.padding(vertical = 8.dp))
                    Text(boxStatus, fontSize = 12.sp); Text(mailStatus, fontSize = 12.sp)
                    MeshButton("Add contact / Scan QR") { screen = "add" }
                    LazyColumn(Modifier.weight(1f)) {
                        val peers = (state.contacts.map { it.address } + state.messages.map { it.peer }).distinct()
                        items(peers, key = { it }) { address ->
                            val contact = state.contacts.firstOrNull { it.address == address }
                            Column(Modifier.fillMaxWidth().clickable { selected = address; screen = "chat" }.padding(vertical = 12.dp)) {
                                Text(contact?.name ?: "Unknown sender")
                                Text(contact?.fingerprint ?: address.take(12), fontSize = 12.sp)
                                state.messages.lastOrNull { it.peer == address }?.let { Text(it.text, maxLines = 1, fontSize = 12.sp) }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}
@Composable private fun Field(value: String, change: (String) -> Unit, label: String, singleLine: Boolean = true) {
    OutlinedTextField(value, change, Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = singleLine, shape = RoundedCornerShape(3.dp))
}
internal fun contactQrBitmap(text: String): Bitmap {
    val bits = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 600, 600, mapOf(com.google.zxing.EncodeHintType.CHARACTER_SET to "UTF-8"))
    val pixels = IntArray(bits.width * bits.height) { i -> if (bits[i % bits.width, i / bits.width]) android.graphics.Color.BLACK else android.graphics.Color.WHITE }
    return Bitmap.createBitmap(pixels, bits.width, bits.height, Bitmap.Config.ARGB_8888)
}

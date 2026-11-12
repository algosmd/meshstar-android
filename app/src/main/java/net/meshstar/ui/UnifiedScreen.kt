package net.meshstar.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import net.meshstar.data.*
import net.meshstar.rnslite.hex
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** UI-only entry also rendered by synthetic snapshot tests; no transport or credentials in fixtures. */
@Composable fun UnifiedScreen(model: UiSnapshot, onAction: suspend (UiAction) -> Boolean,
    language: String = "", appearance: String = "system", feedback: String? = null, busy: Boolean = false,
    requestedPeer: String = "", sharedDraft: String = "", incoming: String = "", boxImport: Pair<String,String>? = null,
    initialPage: String = "chats", initialTab: ChatTab = ChatTab.BLUETOOTH, initialRoutes: Boolean = false) {
    var page by remember { mutableStateOf(initialPage) }
    var tab by remember { mutableStateOf(initialTab) }
    var selected by remember { mutableStateOf(requestedPeer) }
    var search by remember { mutableStateOf("") }
    var plus by remember { mutableStateOf(false) }
    var path by remember { mutableStateOf(PathChoice.AUTO) }
    var routes by remember { mutableStateOf(initialRoutes) }
    var input by remember { mutableStateOf(sharedDraft) }
    var qrText by remember { mutableStateOf("") }
    var name by remember { mutableStateOf(model.name) }
    var boxUrl by remember { mutableStateOf(model.boxUrl) }
    var token by remember { mutableStateOf(model.boxToken) }
    var internet by remember { mutableStateOf(model.internet) }
    var copied by remember { mutableStateOf(false) }
    var nodeSlot by remember { mutableStateOf(model.nodeConfig?.slot?.toString() ?: "7") }
    var nodeSecret by remember { mutableStateOf("") }
    var nodeSelected by remember { mutableStateOf(model.nodeConfig?.let { it.device to it.name }) }
    var replaceConfirm by remember { mutableStateOf(false) }
    var scanningSecret by remember { mutableStateOf(false) }
    var secretDetails by remember { mutableStateOf(false) }
    var linkMode by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val keyboardController=LocalSoftwareKeyboardController.current
    val keyboardVisible=WindowInsets.ime.getBottom(LocalDensity.current)>0
    val scope = rememberCoroutineScope()
    val uiPrefs=remember { context.getSharedPreferences("meshstar.ui",Context.MODE_PRIVATE) }
    var showPowerHint by remember { mutableStateOf(!uiPrefs.getBoolean("miuiHintSeen",false)) }
    val miuiDevice=android.os.Build.MANUFACTURER.equals("Xiaomi",true)
    fun appSettings(){context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+context.packageName)))}
    val peer = model.peers.firstOrNull { it.id == selected }
    val timeline = if (selected == "public") model.publicMessages else model.messages[selected].orEmpty()
    fun act(action: UiAction) { scope.launch { onAction(action) } }
    fun open(id: String) { selected = id; page = "chat"; routes = false; plus = false; path = PathChoice.AUTO }
    fun navigate(value: String) { keyboardController?.hide();page = value; plus = false; routes = false; copied = false }
    fun copy(value: String) {
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("MeshStar QR", value)); copied = true
    }
    LaunchedEffect(requestedPeer) { if (requestedPeer.isNotEmpty() && (selected != requestedPeer || page == "chats")) open(requestedPeer) }
    LaunchedEffect(sharedDraft) { if (sharedDraft.isNotEmpty()) input = sharedDraft }
    LaunchedEffect(incoming) { if (incoming.isNotEmpty()) { qrText = incoming; page = "scan" } }
    LaunchedEffect(boxImport) { boxImport?.let { boxUrl = it.first; token = it.second; page = "box" } }
    LaunchedEffect(page, selected, timeline.lastOrNull()?.id) { if (page == "chat") onAction(UiAction.Read(selected)) }
    BackHandler(page != "chats" || plus || routes) { if (plus) plus = false else if (routes) routes = false else page = "chats" }
    Surface(Modifier.fillMaxSize().testTag("meshstar.unified").safeDrawingPadding().imePadding(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                if (page != "chats") MeshIconButton(tr(T.back), Icons.Outlined.ArrowBack) { navigate(if (page == "contact") "chat" else "chats") }
                Text("MeshStar/@${model.name}", Modifier.weight(1f).heightIn(min = 44.dp).clickable { navigate("settings") }.padding(vertical = 12.dp),
                    maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                MeshIconButton(tr(T.my_qr), Icons.Outlined.QrCode, paintOffset = 5) { navigate("qr") }
                MeshIconButton(tr(T.add), Icons.Outlined.Add, paintOffset = -5) { keyboardController?.hide();plus = !plus }
            }
            if (plus) MeshCard {
                MeshButton(tr(T.add_contact)) { navigate("scan") }
                MeshButton(tr(T.box_settings)) { navigate("box") }
                MeshButton(tr(T.node)) { navigate("node") }
                MeshButton(tr(T.settings)) { navigate("settings") }
            }
            if(page=="chats"&&miuiDevice&&showPowerHint&&!keyboardVisible)MeshCard{
                Secondary(tr(T.miui_hint),size=10)
                Row{MeshButton(tr(T.app_settings)){appSettings()};MeshButton(tr(T.done)){showPowerHint=false;uiPrefs.edit().putBoolean("miuiHintSeen",true).apply()}}
            }
            if (feedback != null) Secondary(feedback, size = 12)
            if (copied) Secondary(tr(T.copied))
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
            when (page) {
                "chats" -> {
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChatTab.entries.forEach { item ->
                            val unread = model.unreadByTab[item] ?: (model.peers.filter { item in it.paths }.sumOf { it.unread } + if (item == ChatTab.BLUETOOTH) model.unreadPublic else 0)
                            Box(Modifier.weight(1f).heightIn(min = 44.dp).clickable(role = Role.Tab) { tab = item; plus = false }
                                .semantics { this.selected = tab == item; contentDescription = context.getString(item.title.resource) }, contentAlignment = Alignment.Center) {
                                Row(Modifier.fillMaxWidth().heightIn(min = 28.dp).border(if (tab == item) 1.dp else .5.dp,
                                    if (tab == item) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, MeshShape)
                                    .background(if (tab == item) MaterialTheme.colorScheme.primary.copy(alpha = .09f) else Color.Transparent, MeshShape)
                                    .padding(horizontal = 3.dp, vertical = 4.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                    RouteIcon(item, 12); Spacer(Modifier.width(3.dp))
                                    Text(tr(item.title), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                MeshBadge(unread, Modifier.align(Alignment.TopEnd))
                            }
                        }
                    }
                    CompactField(search, { search = it }, tr(T.search), Modifier.fillMaxWidth(), tag = "meshstar.search")
                    if (tab == ChatTab.BLUETOOTH) {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                            Secondary(tr(T.nearby), Modifier.padding(end = 8.dp))
                            if (model.peers.none { it.nearby }) Secondary(tr(T.no_people), size = 10)
                            model.peers.filter { it.nearby }.forEach { nearby -> MeshButton(nearby.name + if (nearby.verified) " ✓" else "") { selected = nearby.id; navigate("contact") } }
                        }
                        if (!model.bleReady) MeshButton(tr(T.bluetooth_setup)) { act(UiAction.BluetoothSetup) }
                    }
                    if (tab == ChatTab.LORA) MeshButton(if(model.node.companion) model.node.name + " · #" + model.node.slot else nodeStatusText(model.nodeStatus)) { navigate("node") }
                    if (tab == ChatTab.SATELLITE) {
                        Secondary(localizedFailure(context, model.boxStatus), size = 10)
                        if (model.pointOnline) Secondary("🌐 " + tr(T.via_point), size = 10)
                        Secondary(localizedFailure(context, model.mailStatus), size = 10)
                    }
                    Secondary(tr(T.conversations), Modifier.padding(vertical = 6.dp))
                    val rows = model.peers.filter { tab in it.paths && (search.isBlank() || it.name.contains(search, true)) }
                    LazyColumn((if (tab == ChatTab.BLUETOOTH) Modifier.heightIn(max = 180.dp) else Modifier.weight(1f)).testTag("meshstar.contacts")) {
                        items(rows, key = { it.id }) { row ->
                            Box(Modifier.fillMaxWidth().clickable { open(row.id) }.padding(vertical = 6.dp).heightIn(min = 44.dp).testTag("peer.${row.id}")) {
                                Column(Modifier.padding(end = 22.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(row.name, Modifier.weight(1f), color = peerColor(row.id, LocalMeshDark.current), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        if (row.verified) Text("✓", fontSize = 12.sp)
                                        row.paths.forEach { RouteIcon(it, 12) }
                                        if (row.at > 0) Secondary(time(row.at), size = 10)
                                    }
                                    Secondary(row.preview.ifEmpty { row.fingerprint.take(24) }, size = 12, maxLines = 1)
                                }
                                MeshBadge(row.unread, Modifier.align(Alignment.TopEnd))
                            }
                            HorizontalDivider(thickness = .5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                        }
                        if (rows.isEmpty()) item { Secondary(tr(T.no_contacts), Modifier.padding(vertical = 12.dp)) }
                    }
                    if (tab == ChatTab.BLUETOOTH) {
                        Secondary(tr(T.public_chat), Modifier.padding(top = 6.dp), size = 12)
                        LaunchedEffect(model.publicMessages.lastOrNull()?.id) { onAction(UiAction.Read("public")) }
                        LazyColumn(Modifier.weight(1f), reverseLayout = true, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            items(model.publicMessages.asReversed(), key = { it.id }) { MessageRow(it) }
                        }
                        Composer(input, { input = it }, enabled = model.bleReady && !busy && input.isNotBlank()) {
                            val draft = input; scope.launch { if (onAction(UiAction.Send("public", draft, PathChoice.AUTO)) && input == draft) input = "" }
                        }
                    }
                }
                "chat" -> {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (selected == "public") tr(T.public_chat) else peer?.name ?: tr(T.unknown), Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (selected != "public") MeshIconButton(tr(T.contact), Icons.Outlined.VerifiedUser) { navigate("contact") }
                    }
                    if(model.node.companion) MeshButton(model.node.name + " · LoRa · " + (model.nodeRssi?.let { "$it dBm · " } ?: "") + "#${model.node.slot}") { navigate("node") }
                    if (model.pointOnline && peer?.rns != null && resolvePath(path, peer.nearby && model.bleReady, model.internet.isNotEmpty(), model.boxUrl.isNotEmpty(),model.node.ready,model.relayAvailable) == "satellite") Secondary("🌐 " + tr(T.via_point), size = 10)
                    LazyColumn(Modifier.weight(1f).testTag("meshstar.timeline"), reverseLayout = true, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        items(timeline.asReversed(), key = { it.id }) { message -> MessageRow(message) }
                    }
                    if (selected != "public" && peer?.rns != null) {
                        if (routes) MeshCard {
                            PathChoice.entries.forEach { choice ->
                                Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable {
                                    path = choice; routes = false
                                }.semantics { role = Role.RadioButton; this.selected = choice == path }, verticalAlignment = Alignment.CenterVertically) {
                                    PathIcon(choice); Text(tr(choice.title), Modifier.weight(1f).padding(start = 6.dp), fontSize = 12.sp)
                                    if (choice == path) Text("✓", fontSize = 12.sp)
                                }
                            }
                            if(!model.node.ready)Secondary(tr(T.radio_unavailable), size = 10)
                        }
                        Row(Modifier.heightIn(min = 44.dp).clickable(role = Role.Button) { keyboardController?.hide();routes = !routes }.testTag("meshstar.route"), verticalAlignment = Alignment.CenterVertically) {
                            PathIcon(path); Text(tr(path.title), Modifier.padding(horizontal = 6.dp), fontSize = 12.sp); Icon(Icons.Outlined.ExpandMore, null, Modifier.size(14.dp))
                        }
                    }
                    if ((path == PathChoice.LORA || path == PathChoice.RELAY) && !model.node.ready) Secondary(tr(T.radio_unavailable), size = 10)
                    val enabled = !busy && input.isNotBlank() && if(selected.startsWith("channel:")) model.node.companion && model.node.channels.any { "channel:"+it.name.lowercase()==selected && !it.transport } else if(selected.startsWith("native:")) model.node.companion && model.node.contacts.any { "native:"+it.key.hex()==selected } else if (selected == "public" || peer?.rns == null) model.bleReady else
                        resolvePath(path, peer.nearby && model.bleReady, model.internet.isNotEmpty(), model.boxUrl.isNotEmpty(),model.node.ready,model.relayAvailable) != null
                    Composer(input, { input = it }, enabled) {
                        val draft = input
                        scope.launch { if (onAction(UiAction.Send(selected, draft, path)) && input == draft) input = "" }
                    }
                }
                "scan" -> {
                    Secondary(tr(T.add_contact), Modifier.padding(vertical = 6.dp))
                    ContactScanner(onCode = { value -> scope.launch {
                        if (onAction(UiAction.Import(value))) runCatching { net.meshstar.link.Contact.parse(value) }.getOrNull()?.let { open("rns:${it.address}") }
                    } }, initialValue = qrText)
                }
                "qr" -> ScrollPage {
                    Text(tr(T.my_qr))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CompactField(name, { name = it }, tr(T.nickname), Modifier.weight(1f))
                        MeshButton(tr(T.save)) { act(UiAction.Rename(name)) }
                    }
                    if (model.myQr.isNotEmpty()) {
                        val bitmap = remember(model.myQr) { contactQrBitmap(model.myQr) }
                        Image(bitmap.asImageBitmap(), tr(T.my_qr), Modifier.sizeIn(maxWidth = 200.dp, maxHeight = 200.dp).fillMaxWidth().aspectRatio(1f).align(Alignment.CenterHorizontally))
                    }
                    Secondary(model.myAddress, size = 10); Secondary(tr(T.fingerprint) + ": " + model.myFingerprint, size = 10)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MeshButton(tr(T.copy_qr)) { copy(model.myQr) }
                        MeshButton(tr(T.share)) {
                            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
                                .putExtra(Intent.EXTRA_TEXT, model.myQr), null))
                        }
                    }
                    Secondary(tr(T.qr_hint))
                }
                "contact" -> ScrollPage {
                    Text(peer?.name ?: tr(T.unknown))
                    if (!peer?.fingerprint.isNullOrEmpty()) {
                        if (peer!!.qr.isNotEmpty()) {
                            val bitmap = remember(peer.qr) { contactQrBitmap(peer.qr) }
                            Image(bitmap.asImageBitmap(), tr(T.qr), Modifier.size(156.dp).align(Alignment.CenterHorizontally))
                        }
                        Secondary(tr(T.fingerprint)); Text(peer.fingerprint, fontSize = 12.sp)
                        MeshButton(tr(if (peer.verified) T.unverify else T.verify), Modifier.testTag("meshstar.verify")) { act(UiAction.Verify(peer.id, !peer.verified)) }
                        Secondary(tr(T.compare), size = 10)
                    } else Secondary(tr(T.unknown_hint))
                    MeshButton(tr(T.scan)) { navigate("scan") }
                    if (peer?.ble != null && peer.rns == null && peer.bleFingerprint != null) {
                        MeshButton(tr(T.link_contact)) { linkMode = !linkMode }
                        if (linkMode) {
                            Secondary(tr(T.link_hint), size = 10)
                            model.peers.filter { it.rns != null }.forEach { target ->
                                MeshButton(target.name) { act(UiAction.Link(peer.id, target.id)); linkMode = false }
                            }
                        }
                    }
                }
                "box" -> ScrollPage {
                    Text(tr(T.box_settings))
                    MeshCard {
                        Secondary(localizedFailure(context, model.boxStatus), size = 10)
                        CompactField(boxUrl, { boxUrl = it }, tr(T.box_url)); CompactField(token, { token = it }, tr(T.box_token), password = true)
                        MeshButton(tr(T.scan)) { navigate("scan") }
                    }
                    MeshCard { MeshButton(tr(T.check), enabled = !busy) { act(UiAction.SaveBox(boxUrl, token)) }; MeshButton(tr(T.disable_box), enabled = !busy) { act(UiAction.DisableBox) } }
                    MeshCard {
                        Text(tr(T.internet_server)); Secondary(localizedFailure(context, model.mailStatus), size = 10)
                        CompactField(internet, { internet = it }, tr(T.internet_url)); Secondary(tr(T.internet_hint), size = 10)
                        MeshButton(tr(T.save), enabled = !busy) { act(UiAction.SaveInternet(internet)) }
                    }
                    Secondary(tr(T.background_hint), size = 10)
                }
                "node" -> ScrollPage {
                    Text(tr(T.node));Secondary(nodeStatusText(model.nodeStatus))
                    if(model.node.companion)Secondary(model.node.name+" · "+(model.nodeRssi?.let { "$it dBm · " }?:"")+"#${model.node.slot}")
                    MeshCard {
                        MeshButton(tr(T.node_scan)){act(UiAction.ScanNode)}
                        model.nodeDevices.forEach { device->MeshButton(device.name+" · ${device.rssi} dBm"+if(nodeSelected?.first==device.id)" ✓" else ""){nodeSelected=device.id to device.name} }
                        nodeSelected?.let { Secondary(it.second) }
                        CompactField(nodeSlot,{nodeSlot=it.filter(Char::isDigit).take(2)},tr(T.node_slot))
                        CompactField(nodeSecret,{nodeSecret=it},tr(T.node_secret),password=true,tag="meshstar.node.secret")
                        MeshButton(tr(T.scan)){scanningSecret=!scanningSecret}
                        if(scanningSecret)ContactScanner(onCode={nodeSecret=it;scanningSecret=false})
                        MeshButton(tr(T.more)){secretDetails=!secretDetails}
                        if(secretDetails)Secondary(tr(T.node_secret_help),size=10)
                        MeshButton(tr(T.node_connect),enabled=nodeSelected!=null && nodeSlot.toIntOrNull() in 0..39){nodeSelected?.let{act(UiAction.ConfigureNode(it.first,it.second,nodeSlot.toIntOrNull()?:7,nodeSecret))}}
                        if(model.nodeConflict){
                            Secondary(tr(T.node_occupied),size=10)
                            MeshButton(tr(T.node_replace)){replaceConfirm=true}
                            if(replaceConfirm){Secondary(tr(T.node_replace_warning),size=10);MeshButton(tr(T.node_replace_confirm),enabled=nodeSecret.isNotBlank() && nodeSlot.toIntOrNull() in 0..39){nodeSelected?.let{act(UiAction.ConfigureNode(it.first,it.second,nodeSlot.toIntOrNull()?:7,nodeSecret,true));replaceConfirm=false}}}
                        }
                        MeshButton(tr(T.node_forget)){act(UiAction.ForgetNode)}
                    }
                    if(model.node.ready)Secondary(tr(T.node_channel_ready))
                    if(model.node.companion){
                        MeshCard { MeshButton(tr(T.node_refresh)){act(UiAction.RefreshNode)};MeshButton(tr(T.node_advertise)){act(UiAction.AdvertiseNode)} }
                        Secondary(tr(T.node_contacts));model.node.contacts.forEach { contact->MeshButton(contact.name){act(UiAction.OpenNative(contact.key.hex()));open("native:"+contact.key.hex())} }
                        Secondary(tr(T.node_adverts));model.node.advertisements.forEach { Secondary(it.name,size=12) }
                        model.node.channels.filterNot{it.transport || it.name.isBlank()}.forEach{channel->MeshButton("#"+channel.name){open("channel:"+channel.name.lowercase())}}
                    }
                }
                "language" -> ScrollPage {
                    Text(tr(T.language))
                    listOf("" to T.system, "en" to null, "ru" to null, "ro" to null, "de" to null, "fr" to null).forEach { (code, key) ->
                        val label = key?.let { tr(it) } ?: Locale.forLanguageTag(code).getDisplayLanguage(Locale.forLanguageTag(code))
                        MeshButton(label + if (code == language) " ✓" else "") { act(UiAction.Language(code)); page = "settings" }
                    }
                }
                else -> ScrollPage {
                    Text(tr(T.settings))
                    MeshCard { MeshButton(tr(T.box_settings), Modifier.fillMaxWidth()) { navigate("box") }; MeshButton(tr(T.node), Modifier.fillMaxWidth()) { navigate("node") } }
                    MeshCard {
                        Text(tr(T.appearance))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("system" to T.system, "light" to T.light, "dark" to T.dark).forEach { (value, text) ->
                                MeshButton(tr(text) + if (value == appearance) " ✓" else "") { act(UiAction.Appearance(value)) }
                            }
                        }
                        MeshButton(tr(T.language), Modifier.fillMaxWidth()) { navigate("language") }
                    }
                    MeshCard { MeshButton(tr(T.bluetooth_setup)) { act(UiAction.BluetoothSetup) }; Secondary(tr(T.background_hint), size = 10) }
                    if(miuiDevice)MeshCard { Secondary(tr(T.miui_hint),size=10);MeshButton(tr(T.app_settings)){appSettings()} }
                    Secondary(tr(T.version) + " " + com.bitchat.android.BuildConfig.VERSION_NAME + " (" + com.bitchat.android.BuildConfig.VERSION_CODE + ")", size = 10)
                }
            }
        }
    }
}
@Composable private fun ScrollPage(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}
@Composable fun CompactField(value: String, change: (String) -> Unit, label: String, modifier: Modifier = Modifier.fillMaxWidth(),
                            singleLine: Boolean = true, password: Boolean = false, tag: String = "") {
    BasicTextField(value, change, modifier.heightIn(min = 44.dp).testTag(tag),
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.primary),
        singleLine = singleLine, maxLines = if (singleLine) 1 else 4,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { inner ->
            Box(Modifier.padding(vertical = 5.dp).border(.5.dp, MaterialTheme.colorScheme.outline, MeshShape).padding(horizontal = 6.dp, vertical = 6.dp)) {
                if (value.isEmpty()) Secondary(label, size = 12)
                inner()
            }
        })
}
@Composable fun RouteIcon(tab: ChatTab, size: Int = 14) {
    when (tab) {
        ChatTab.BLUETOOTH -> Icon(Icons.Outlined.Bluetooth, tr(T.bluetooth), Modifier.size(size.dp))
        ChatTab.LORA -> Icon(Icons.Outlined.SettingsInputAntenna, tr(T.lora), Modifier.size(size.dp))
        ChatTab.SATELLITE -> Text("🛰", fontSize = size.sp)
    }
}
@Composable fun PathIcon(path: PathChoice) {
    when (path) {
        PathChoice.LORA, PathChoice.RELAY -> Icon(Icons.Outlined.SettingsInputAntenna, tr(path.title), Modifier.size(14.dp))
        PathChoice.SATELLITE -> Text("🛰", fontSize = 14.sp)
        PathChoice.INTERNET -> Text("🌐", fontSize = 14.sp)
        PathChoice.AUTO -> Icon(Icons.Outlined.AltRoute, null, Modifier.size(14.dp))
    }
}
@Composable private fun MessageRow(message: UiMessage) {
    val color = if (message.outgoing) Color(0xFFFF9500) else peerColor(message.peer, LocalMeshDark.current)
    Column(Modifier.fillMaxWidth()) {
        Text(buildAnnotatedString {
            withStyle(SpanStyle(color = color, fontSize = 14.sp)) { append("<${message.name}> ${message.text}") }
            withStyle(SpanStyle(color = Color.Gray.copy(alpha = .7f), fontSize = 10.sp)) { append(" [${time(message.at)}]") }
        })
        if (message.route != null) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    if (message.viaPoint) { Icon(Icons.Outlined.SettingsInputAntenna, null, Modifier.size(10.dp)); Secondary("→🌐 " + tr(T.via_point), size = 10, maxLines = 1) }
                    else if(message.route in setOf(Route.LORA,Route.RELAY)){
                        Icon(Icons.Outlined.SettingsInputAntenna,null,Modifier.size(10.dp))
                        Secondary(" "+tr(if(message.route==Route.RELAY)T.relay else T.lora)+(message.hops?.let{" · "+LocalContext.current.getString(T.hops_format.resource,it)}?:"")+(message.snr?.let{" · SNR $it dB"}?:""),size=10,maxLines=1)
                    } else Secondary(if (message.route == Route.INTERNET) "🌐 " + tr(T.internet) else "🛰 " + tr(T.satellite), size = 10, maxLines = 1)
                }
                message.signature?.let { Secondary(tr(if (it) T.signature_ok else T.signature_bad), size = 10, maxLines = 1) }
            }
        }
        if(message.fromBox)Secondary(tr(T.from_box),size=10)
        if (message.outgoing && message.showDelivery) Secondary(tr(when (message.delivery) { Delivery.QUEUED -> T.queued; Delivery.SENT -> T.sent; Delivery.DELIVERED -> T.delivered }), size = 10)
    }
}
private fun time(at: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(at))

@Composable private fun Composer(value: String, change: (String) -> Unit, enabled: Boolean, send: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.Bottom) {
        CompactField(value, change, tr(T.message), Modifier.weight(1f), singleLine = false, tag = "meshstar.message")
        MeshIconButton(tr(T.send), Icons.Outlined.ArrowUpward, composer = true, enabled = enabled, onClick = send)
    }
}

@Composable private fun nodeStatusText(value:String):String = tr(when(value){
    "node_scanning"->T.node_scanning;"node_connecting"->T.node_connecting;"node_reading"->T.node_reading;
    "node_ready"->T.node_ready;"node_text_only"->T.node_text_only;"node_occupied"->T.node_occupied;
    "node_secret"->T.node_secret_help;"node_mtu"->T.node_mtu;"node_permission"->T.node_permission;
    "node_failed"->T.node_failed;else->T.node_unavailable
})

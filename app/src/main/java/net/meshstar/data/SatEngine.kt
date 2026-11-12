package net.meshstar.data

import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.meshstar.box.*
import net.meshstar.link.Contact
import net.meshstar.meshcore.*
import net.meshstar.mailbox.MailboxTransport
import net.meshstar.rnslite.*
import java.time.Instant
import java.time.ZoneOffset

/** Network awaits never hold the state mutex. Every receive persists data + proof
 * before ACK/cursor advancement. Retrying uses the original ciphertext/hash. */
class SatEngine(val identity: Identity, private val store: StateStore,
                private val http: CarrierHttp = CarrierHttp(), private val now: () -> Instant = { Instant.now() }) {
    private val mutex = Mutex()
    private val checking = Mutex()
    private val gson = Gson()
    private var closed = false
    private val mutable = MutableStateFlow(store.load())
    val state = mutable.asStateFlow()
    private val point = MutableStateFlow(false)
    val pointOnline = point.asStateFlow()
    suspend fun refreshBoxStatus() {
        val config = state.value.box ?: run { point.value = false; return }
        val status = try { BoxTransport(config, http).statusDetails() } catch (e: Exception) { point.value = false; throw e }
        if (status.address != config.address) { point.value = false; error("Box identity changed") }
        if (state.value.box == config) {
            point.value = status.pointOnline
            if(status.transportId!=null && status.transportId!=config.transportId) change { if(it.box==config)it.box=config.copy(transportId=status.transportId) }
        }
    }
    suspend fun initializeReadTracking() {
        if (!state.value.readTrackingInitialized) change { next ->
            next.messages.filter { !it.outgoing }.groupBy { it.peer }.forEach { (peer, messages) ->
                next.readThrough[peer] = messages.maxOf { it.at }
            }
            next.readTrackingInitialized = true
        }
    }
    suspend fun markRead(peer: String) { change { it.readThrough[peer] = now().toEpochMilli() } }
    suspend fun verifyContact(address: String, verified: Boolean) { change { next ->
        val contact = next.contacts.firstOrNull { it.address == address } ?: error("Unknown contact")
        if (verified) next.verifiedKeys[address] = contact.key else next.verifiedKeys.remove(address)
    } }
    suspend fun linkBluetooth(fingerprint: String, address: String) { change { next ->
        require(fingerprint.isNotBlank() && next.contacts.any { it.address == address })
        next.bluetoothLinks[fingerprint] = address
    } }

    private fun copy(state: SatState) = gson.fromJson(gson.toJson(state), SatState::class.java)
    private suspend fun change(block: (SatState) -> Unit) = mutex.withLock {
        check(!closed) { "Restart after data reset" }
        val next = copy(mutable.value); block(next); store.save(next); mutable.value = next
    }
    suspend fun closeForReset() = mutex.withLock { closed = true; mutable.value = SatState() }
    fun myContact() = Contact.of(identity, state.value.name)
    suspend fun setName(name: String) { change { it.name = name.trim().take(80).ifEmpty { "MeshStar" } } }
    suspend fun addContact(qr: String): Contact {
        val contact = Contact.parse(qr)
        require(contact.address != identity.lxmfAddress.hex()) { "This is your own contact" }
        change { addContact(it, contact) }; return contact
    }
    private fun addContact(state: SatState, contact: Contact) {
        val old = state.contacts.firstOrNull { it.address == contact.address }
        require(old == null || old.key == contact.key) { "Contact public key changed" }
        require(old != null || state.contacts.size < 1000) { "Contact limit reached" }
        state.contacts.removeAll { it.address == contact.address }; state.contacts.add(contact)
    }
    suspend fun configureBox(url: String, credential: String): String = checking.withLock {
        val endpoint = CarrierHttp.endpoint(url).toString().trimEnd('/')
        val token = credential.trim()
        require(token.isNotEmpty() && token.length <= 4096 && token.all { it.code in 32..126 }) { "Enter a valid box token" }
        val provisional = BoxConfig(endpoint, token)
        val status = BoxTransport(provisional, http).statusDetails()
        val address = status.address
        val candidate = provisional.copy(address = address, transportId = status.transportId)
        val (boxContact, pairingId) = BoxTransport(candidate, http).pairDetails(myContact())
        require(pairingId == null || status.transportId == null || pairingId == status.transportId) { "Box identity changed" }
        val config = candidate.copy(transportId = pairingId ?: status.transportId)
        change { it.box = config; addContact(it, boxContact); it.boxCursors.putIfAbsent(address, BoxCursor()) }
        point.value = status.pointOnline
        address
    }
    suspend fun disableBox() { change { it.box = null }; point.value = false }
    suspend fun configureInternet(url: String) {
        val endpoint = if (url.isBlank()) "" else CarrierHttp.endpoint(url).toString().trimEnd('/')
        change { it.internet = endpoint }
    }
    suspend fun send(peer: String, text: String, route: Route): String {
        require(text.isNotBlank()) { "Enter a message" }
        val s = state.value
        val contact = s.contacts.firstOrNull { it.address == peer } ?: error("Add the contact first")
        val carrier = when (route) {
            Route.LORA, Route.RELAY -> s.node?.publicKey ?: error("Configure the node first")
            Route.SATELLITE -> s.box?.address?.takeIf { it.isNotEmpty() } ?: error("Configure the box first")
            Route.INTERNET -> s.internet.takeIf { it.isNotEmpty() }?.let(::carrierId) ?: error("Configure the internet server first")
        }
        require(route != Route.SATELLITE || peer != carrier) { "A box cannot send to itself through satellite" }
        val packet = LXMessage.create(identity, contact.identity, text.toByteArray(Charsets.UTF_8), now().toEpochMilli() / 1000.0).packet(contact.identity)
        val relayId = if(route==Route.RELAY)s.box?.transportId?:error("Configure the relay identity first") else null
        if(relayId!=null)packet.via(relayId.unhex()) // Validate HEADER_2 size before durable admission.
        change {
            require(it.messages.count { m -> m.outgoing && !m.submitted } < 1000) { "Outbox is full" }
            require(it.messages.size < 10_000) { "History is full" }
            it.messages.add(ChatMessage(packet.hash.hex(), peer, true, text, packet.raw.hex(), route, carrier, now().toEpochMilli(), viaPoint = route in setOf(Route.SATELLITE,Route.RELAY) && point.value, relayTransportId=relayId))
        }
        return packet.hash.hex()
    }
    suspend fun flushOutgoing(route: Route) {
        val s = state.value
        val carrier = if (route == Route.SATELLITE) s.box?.address ?: return else s.internet.takeIf { it.isNotEmpty() }?.let(::carrierId) ?: return
        for (message in s.messages.filter { it.outgoing && !it.submitted && it.route == route && it.carrier == carrier }) {
            val packet = Packet(message.raw.unhex())
            val id = if (route == Route.SATELLITE) BoxTransport(s.box!!, http).send(packet) else {
                val contact = s.contacts.firstOrNull { it.address == message.peer } ?: continue
                MailboxTransport(s.internet, http).drop(packet.raw, mailboxTo(contact))
            }
            change { next -> next.messages.firstOrNull { it.hash == message.hash }?.let {
                it.submitted = true; it.serverId = id
                if (route == Route.SATELLITE) it.viaPoint = point.value
                if (route == Route.INTERNET && it.delivery != Delivery.DELIVERED) it.delivery = Delivery.SENT
            } }
        }
    }
    suspend fun pollBox() {
        val config = state.value.box ?: return
        val transport = BoxTransport(config, http)
        val before = state.value
        val unresolved = before.messages.filter { it.outgoing && it.carrier == config.address && it.delivery != Delivery.DELIVERED }
        var since = if (unresolved.any { it.serverId == 0L }) 0 else minOf(before.boxCursors[config.address]?.sent ?: 0,
            unresolved.minOfOrNull { maxOf(0, it.serverId - 1) } ?: Long.MAX_VALUE)
        // IDs represent messages, not status changes: revisit unresolved IDs on every poll.
        for (page in 0 until 20) {
            val rows = transport.sent(since)
            change { next ->
                val cursor = next.boxCursors.getOrPut(config.address) { BoxCursor() }
                for (row in rows) {
                    cursor.sent = maxOf(cursor.sent, row.id)
                    next.messages.filter { it.outgoing && it.hash == row.hash }.forEach { message ->
                        if (message.delivery != Delivery.DELIVERED && row.status in listOf("orbit", "sent", "delivered")) message.delivery = Delivery.SENT
                    }
                }
            }
            if (rows.size < 500) break
            since = rows.maxOf { it.id }
        }
        val cursor = state.value.boxCursors[config.address]?.inbox ?: 0
        val rows = transport.inbox(cursor).sortedBy { it.id }
        for (row in rows) {
            change { next ->
                consume(next, row.raw, row.kind, Route.SATELLITE, config.address)
                next.boxCursors.getOrPut(config.address) { BoxCursor() }.inbox = row.id
            }
        }
        flushProofs(Route.SATELLITE, config.address, config.url, config)
    }
    private fun mailboxTo(contact: Contact): ByteArray = Mailbox.address(identity, contact.identity, contact.identity.lxmfAddress,
        now().atZone(ZoneOffset.UTC).toLocalDate())
    suspend fun pollMailbox(wait: Int = 25) {
        val s = state.value
        if (s.internet.isBlank() || s.contacts.isEmpty()) return
        val carrier = carrierId(s.internet)
        val transport = MailboxTransport(s.internet, http)
        val groups = s.contacts.flatMap { Mailbox.pickupAddresses(identity, it.identity, now()).map(ByteArray::hex) }.distinct().sorted().chunked(255)
        for ((index, addresses) in groups.withIndex()) {
            val key = carrier + ":" + Hash.sha256(addresses.joinToString(",").toByteArray()).hex()
            val cursor = state.value.mailCursors[key] ?: 0
            val pickup = transport.pickup(addresses, cursor, if (index == 0) wait else 0)
            for (item in pickup.items) change { consume(it, item.raw.hex(), item.kind, Route.INTERNET, carrier) }
            // A received message and its pending proof are durable first. If proof/drop or ACK
            // fails, keep the old cursor. Replay is deduplicated and can recreate the proof.
            flushProofs(Route.INTERNET, carrier, s.internet)
            if (pickup.items.isNotEmpty()) transport.ack(pickup.items.map { it.id }, addresses)
            change {
                it.mailCursors[key] = pickup.next
                while (it.mailCursors.size > 64) it.mailCursors.remove(it.mailCursors.keys.first())
            }
        }
    }
    private suspend fun flushProofs(route: Route, carrier: String, url: String, box: BoxConfig? = null) {
        for (proof in state.value.proofs.filter { it.route == route && it.carrier == carrier }) {
            val packet = Packet(proof.raw.unhex())
            if (route == Route.SATELLITE) BoxTransport(box!!, http).send(packet, noProof = true)
            else {
                val contact = state.value.contacts.firstOrNull { it.address == proof.peer } ?: continue
                MailboxTransport(url, http).drop(packet.raw, mailboxTo(contact))
            }
            change { it.proofs.remove(proof) }
        }
    }
    private fun consume(state: SatState, hex: String, kind: String, route: Route, carrier: String, reception: Reception? = null) {
        // Invalid carrier rows are consumed without confirmation, not retried forever.
        val raw = runCatching { hex.unhex() }.getOrNull() ?: return
        if (kind == "voice") {
            // Preserve encrypted frames until A6 rather than discard an acknowledged voice note.
            if (raw.size in 133..1400 && raw.copyOfRange(0,5).contentEquals(VoiceFrame.magic) && raw.copyOfRange(5,21).contentEquals(identity.lxmfAddress)) {
                require(state.pendingVoice.size < 1000 || state.pendingVoice.containsKey(Hash.sha256(raw).hex())) { "Voice inbox is full" }
                state.pendingVoice[Hash.sha256(raw).hex()] = hex
            }
            return
        }
        val packet = runCatching { Packet(raw) }.getOrNull() ?: return
        if (packet.type == Packet.PROOF) {
            for (message in state.messages.filter { it.outgoing && Packet(it.raw.unhex()).truncatedHash.contentEquals(packet.destination) }) {
                val contact = state.contacts.firstOrNull { it.address == message.peer } ?: continue
                if (Packet(message.raw.unhex()).validateProof(packet, contact.identity)) {
                    if(message.delivery!=Delivery.DELIVERED && route==Route.LORA && message.route==Route.LORA) {
                        val recent=state.radioObservations[message.peer].orEmpty()
                        message.viaPoint=point.value && recent.none { (hash,at)->hash!=packet.hash.hex()&&at<=now().toEpochMilli()&&now().toEpochMilli()-at<300000 }
                        if(!message.viaPoint)observeRadio(state,message.peer,packet.hash.hex(),reception)
                    }
                    if(route==Route.LORA){message.radioSnr=reception?.snr;message.radioHops=reception?.hops}
                    message.delivery = Delivery.DELIVERED
                }
            }
            return
        }
        if (packet.type != Packet.DATA || !packet.destination.contentEquals(identity.lxmfAddress)) return
        val verified = runCatching { LXMessage.receive(packet, identity) { address -> state.contacts.firstOrNull { it.address == address.hex() }?.identity } }.getOrNull()
        val decoded = if (verified != null) Triple(verified.source.hex(), verified.content, true) else {
            runCatching {
                val plain = identity.decrypt(packet.payload)
                require(plain.size >= 80)
                val fields = MsgPack.decode(plain.copyOfRange(80, plain.size)) as List<*>
                require(fields.size == 4)
                Triple(plain.copyOfRange(0,16).hex(), fields[2] as ByteArray, false)
            }.getOrNull() ?: return
        }
        val hash = packet.hash.hex()
        if(decoded.third && route==Route.LORA)observeRadio(state,decoded.first,hash,reception)
        if (hash !in state.seen && state.messages.none { it.hash == hash }) {
            val text = runCatching {
                if (decoded.second.isNotEmpty() && (decoded.second[0].toInt() and 255) < 8) TextCodec.decode(decoded.second)
                else decoded.second.toString(Charsets.UTF_8)
            }.getOrDefault("[Invalid text encoding]")
            require(state.messages.size < 10_000) { "History is full" }
            state.messages.add(ChatMessage(hash, decoded.first, false, text, hex, route, carrier, now().toEpochMilli(), Delivery.DELIVERED, decoded.third, true, viaPoint = route == Route.SATELLITE && point.value, radioSnr=reception?.snr, radioHops=reception?.hops))
            state.seen.add(hash)
            while (state.seen.size > 5000) state.seen.removeAt(0)
        }
        if (decoded.third) {
            val existing = state.messages.indexOfFirst { it.hash == hash && !it.outgoing && !it.verified }
            if (existing >= 0) state.messages[existing] = state.messages[existing].copy(verified = true)
            val proof = PendingProof(packet.prove(identity).raw.hex(), decoded.first, route, carrier)
            require(state.proofs.size < 1000 || proof in state.proofs) { "Confirmation queue is full" }
            if (proof !in state.proofs) state.proofs.add(proof)
        }
    }
    suspend fun saveNode(config:NodeConfiguration?) { change { it.node=config } }
    suspend fun saveChannels(channels:List<net.meshstar.meshcore.Channel>) { change { it.channelNames.clear();channels.filterNot { c->c.transport || c.name.isBlank() }.forEach { c->it.channelNames[c.index]=c.name } } }
    suspend fun receiveRadio(raw:ByteArray,reception:Reception,carrier:String?=state.value.node?.publicKey) {
        val node=carrier?:return
        val kind=if(raw.take(5).toByteArray().contentEquals(VoiceFrame.magic))"voice" else "data"
        change { consume(it,raw.hex(),kind,Route.LORA,node,reception) }
    }
    suspend fun flushRadio(send:suspend(ByteArray)->Unit) {
        val node=state.value.node?.publicKey?:return
        for(m in state.value.messages.filter { it.outgoing&&!it.submitted&&it.route in setOf(Route.LORA,Route.RELAY)&&it.carrier==node }) {
            val packet=Packet(m.raw.unhex());val wire=if(m.route==Route.RELAY)packet.via(m.relayTransportId?.unhex()?:error("Missing relay identity"))else packet
            send(wire.raw)
            change { next->next.messages.firstOrNull { it.hash==m.hash }?.let { it.submitted=true;if(it.delivery!=Delivery.DELIVERED)it.delivery=Delivery.SENT } }
        }
        for(p in state.value.proofs.filter { it.route==Route.LORA&&it.carrier==node }) {send(p.raw.unhex());change { it.proofs.remove(p) }}
    }
    private fun observeRadio(s:SatState,peer:String,hash:String,reception:Reception?) {
        if(reception==null||!reception.snr.isFinite())return
        val at=now().toEpochMilli();val recent=s.radioObservations.getOrPut(peer){mutableMapOf()}
        recent.entries.removeAll { it.value>at||at-it.value>=300000 };recent[hash]=at
        while(recent.size>32)recent.remove(recent.minBy { it.value }.key)
        s.radioObservations.keys.retainAll(s.contacts.map { it.address }.toSet())
    }
    suspend fun saveNativeContacts(contacts:List<NodeContact>) {
        val next=contacts.associate { it.key.hex() to it.name }
        if(state.value.nativeContacts==next)return
        change { state->
            state.nativeContacts.clear();state.nativeContacts.putAll(next)
            for(i in state.channelTexts.indices){
                val text=state.channelTexts[i]
                if(text.directKey==null&&text.channel.startsWith("direct:")){
                    val prefix=text.channel.removePrefix("direct:")
                    next.keys.filter { it.startsWith(prefix) }.singleOrNull()?.let { key->state.channelTexts[i]=text.copy(channel="direct:"+key,directKey=key) }
                }
            }
        }
    }
    suspend fun openNative(key:String){require(key.unhex().size==32);change { it.nativeChats.add(key) }}
    suspend fun recordDirectSent(key:String,text:String,timestamp:Long){change { s->
        appendChannel(s,"direct:"+key,text,timestamp,now().toEpochMilli(),false,null,null,true,key)
        s.nativeChats.add(key)
    }}
    suspend fun receiveForeign(text:ForeignText) { change { s->
        val prefix=text.prefix?.hex()
        val directKey=prefix?.let { p->s.nativeContacts.keys.filter { it.startsWith(p) }.singleOrNull() }
        val name=text.channel?.let { s.channelNames[it] }?:if(prefix!=null)"direct:"+(directKey?:prefix)else "channel:${text.channel}"
        if(name.trim().equals("MESHSAT",true))return@change
        appendChannel(s,name,text.text,text.timestamp,now().toEpochMilli(),false,text.reception?.snr,text.reception?.hops,directKey=directKey)
    } }
    private fun appendChannel(s:SatState,name:String,text:String,timestamp:Long,receivedAt:Long,fromBox:Boolean,snr:Double?,hops:Int?,outgoing:Boolean=false,directKey:String?=null) {
        val id=Hash.sha256((name.lowercase()+"\u0000"+timestamp+"\u0000"+text).toByteArray()).hex()
        if(s.channelTexts.none { it.id==id || (it.channel.equals(name,true)&&it.timestamp==timestamp&&it.text==text) })s.channelTexts.add(ChannelText(id,name,text,timestamp,receivedAt,fromBox,snr,hops,outgoing,directKey))
        while(s.channelTexts.size>5000)s.channelTexts.removeAt(0)
    }
    suspend fun recordChannelSent(name:String,text:String,timestamp:Long){change{appendChannel(it,name,text,timestamp,now().toEpochMilli(),false,null,null,true)}}
    suspend fun pollChannelHistory() {
        val config=state.value.box?:return;val transport=BoxTransport(config,http)
        for((index,name) in transport.channels().filterNot { it.second.trim().equals("MESHSAT",true) }) {
            val key=config.address+":"+name.lowercase()
            for(page in 0 until 10) {
                val since=state.value.channelCursors[key]?:0.0
                val rows=transport.channelHistory(index,since)
                change { s->rows.forEach { r->appendChannel(s,name,r.text,r.timestamp,(r.ts*1000).toLong(),true,r.snr,r.hops) };s.channelCursors[key]=maxOf(since,rows.maxOfOrNull{it.ts}?:since) }
                if(rows.size<200)break
            }
        }
    }
    companion object { fun carrierId(url: String): String = Hash.sha256(url.toByteArray(Charsets.UTF_8)).hex() }
}

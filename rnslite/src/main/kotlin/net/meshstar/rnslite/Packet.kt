package net.meshstar.rnslite

class Packet(raw: ByteArray) {
    private val bytes = raw.copyOf()
    val raw: ByteArray get() = bytes.copyOf()
    val flags: Int
    val hops: Int
    val type: Int get() = flags and 3
    val destinationType: Int get() = (flags shr 2) and 3
    val hasContextFlag: Boolean get() = flags and 0x20 != 0
    val transportId: ByteArray?
    val destination: ByteArray
    val context: Int
    val payload: ByteArray
    init {
        checkRns(bytes.size in 19..MTU, "Packet length")
        flags = bytes[0].toInt() and 255; hops = bytes[1].toInt() and 255
        val start = if (flags and 0x40 != 0) 18 else 2
        checkRns(bytes.size >= start + 17, "Header 2 length")
        transportId = if (start == 18) bytes.cut(2, 18) else null
        destination = bytes.cut(start, start + 16); context = bytes[start + 16].toInt() and 255
        payload = bytes.cut(start + 17)
    }
    val hash: ByteArray get() = Hash.sha256(byteArrayOf((flags and 15).toByte()) + bytes.cut(if (transportId == null) 2 else 18))
    val truncatedHash: ByteArray get() = hash.cut(0, 16)
    fun via(transportId: ByteArray): Packet {
        checkRns(transportId.size == 16 && this.transportId == null, "Transport header")
        return Packet(byteArrayOf((flags or 0x40).toByte(), 0) + transportId + bytes.cut(2))
    }
    fun prove(identity: Identity): Packet = build(PROOF, truncatedHash, identity.sign(hash))
    fun validateProof(proof: Packet, peer: PublicIdentity): Boolean = validateProofHash(proof, hash, peer)
    companion object {
        const val MTU = 500
        const val DATA = 0
        const val ANNOUNCE = 1
        const val LINK_REQUEST = 2
        const val PROOF = 3
        fun build(type: Int, destination: ByteArray, payload: ByteArray, context: Int = 0): Packet {
            checkRns(type in 0..3 && destination.size == 16 && context in 0..255, "Packet header")
            return Packet(byteArrayOf(type.toByte(), 0) + destination + byteArrayOf(context.toByte()) + payload)
        }
        internal fun validateProofHash(proof: Packet, hash: ByteArray, peer: PublicIdentity): Boolean {
            if (proof.type != PROOF || !Hash.equal(proof.destination, hash.cut(0, 16))) return false
            val signature = when (proof.payload.size) {
                64 -> proof.payload
                96 -> {
                    if (!Hash.equal(proof.payload.cut(0, 32), hash)) return false
                    proof.payload.cut(32)
                }
                else -> return false
            }
            return peer.validate(signature, hash)
        }
    }
}

class LXMessage private constructor(
    val destination: ByteArray, val source: ByteArray, val timestamp: Double,
    val title: ByteArray, val content: ByteArray, val fields: MsgPack.MapValue,
    val payload: ByteArray, val signature: ByteArray,
) {
    val hash: ByteArray get() = Hash.sha256(destination + source + payload)
    val packed: ByteArray get() = destination + source + signature + payload
    val packetPlain: ByteArray get() = source + signature + payload
    fun packet(to: PublicIdentity, ephemeralPrivateKey: ByteArray = randomBytes(32), iv: ByteArray = randomBytes(16)): Packet {
        checkRns(Hash.equal(destination, to.lxmfAddress), "Wrong recipient")
        return Packet.build(Packet.DATA, destination, to.encrypt(packetPlain, ephemeralPrivateKey, iv))
    }
    companion object {
        fun create(from: Identity, to: PublicIdentity, content: ByteArray, timestamp: Double,
                   title: ByteArray = byteArrayOf(), fields: MsgPack.MapValue = MsgPack.MapValue()): LXMessage {
            checkRns(timestamp.isFinite(), "Timestamp")
            val payload = MsgPack.encode(listOf(timestamp, title, content, fields))
            val preimage = to.lxmfAddress + from.lxmfAddress + payload
            return LXMessage(to.lxmfAddress, from.lxmfAddress, timestamp, title.copyOf(), content.copyOf(), fields,
                payload, from.sign(preimage + Hash.sha256(preimage)))
        }
        fun receive(packet: Packet, me: Identity, recall: (ByteArray) -> PublicIdentity?): LXMessage {
            checkRns(packet.type == Packet.DATA && Hash.equal(packet.destination, me.lxmfAddress), "Not for us")
            val plain = me.decrypt(packet.payload)
            checkRns(plain.size >= 80, "LXMF length")
            val source = plain.cut(0, 16); val signature = plain.cut(16, 80); val payload = plain.cut(80)
            val peer = recall(source) ?: throw RNSError("Unknown sender")
            checkRns(Hash.equal(peer.lxmfAddress, source), "Sender address mismatch")
            val preimage = packet.destination + source + payload
            checkRns(peer.validate(signature, preimage + Hash.sha256(preimage)), "Invalid signature")
            val values = MsgPack.decode(payload) as? List<*> ?: throw RNSError("LXMF array")
            checkRns(values.size == 4, "LXMF fields")
            val timestamp = (values[0] as? Number)?.toDouble() ?: throw RNSError("LXMF timestamp")
            checkRns(timestamp.isFinite(), "Timestamp")
            return LXMessage(packet.destination.copyOf(), source, timestamp,
                values[1] as? ByteArray ?: throw RNSError("LXMF title"),
                values[2] as? ByteArray ?: throw RNSError("LXMF content"),
                values[3] as? MsgPack.MapValue ?: throw RNSError("LXMF fields"), payload, signature)
        }
    }
}

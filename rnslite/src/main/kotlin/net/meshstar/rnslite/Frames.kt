package net.meshstar.rnslite

object SatFrame {
    private val magic = byteArrayOf(0x52, 0x4e, 0x53, 0x49, 1)
    enum class Kind { RNS, VOICE, TEXT }
    fun encode(packet: Packet): ByteArray = magic + packet.raw
    fun decode(frame: ByteArray): Packet {
        checkRns(frame.size > magic.size && frame.cut(0, magic.size).contentEquals(magic), "Not RNSI")
        return Packet(frame.cut(magic.size))
    }
    fun classify(frame: ByteArray): Kind = when {
        frame.size > 5 && frame.cut(0, 5).contentEquals(magic) -> Kind.RNS
        frame.size >= 5 && frame.cut(0, 5).contentEquals(VoiceFrame.magic) -> Kind.VOICE
        else -> Kind.TEXT
    }
}
object MeshCoreFrag {
    const val MAGIC_VERSION = 0xa1
    const val MAX_FRAGMENT = 163
    data class Fragment(val packetId: Int, val index: Int, val total: Int, val payload: ByteArray)
    fun split(data: ByteArray, packetId: Int, payloadSize: Int = 159): List<ByteArray> {
        checkRns(packetId in 0..65535 && data.isNotEmpty(), "Fragment parameters")
        val per = payloadSize.coerceIn(16, 159)
        val total = (data.size + per - 1) / per
        checkRns(total <= 15, "Too many fragments")
        return (0 until total).map { index ->
            byteArrayOf((packetId shr 8).toByte(), packetId.toByte(), ((index shl 4) or total).toByte(), MAGIC_VERSION.toByte()) +
                data.cut(index * per, minOf(data.size, (index + 1) * per))
        }
    }
    fun parse(data: ByteArray): Fragment {
        checkRns(data.size in 5..MAX_FRAGMENT && data[3].toInt() and 255 == MAGIC_VERSION, "Fragment header")
        val index = (data[2].toInt() and 255) shr 4; val total = data[2].toInt() and 15
        checkRns(total > 0 && index < total, "Fragment index")
        return Fragment(((data[0].toInt() and 255) shl 8) or (data[1].toInt() and 255), index, total, data.cut(4))
    }
    /** Call from one transport queue. Bounded state, monotonic expiration; no transport dependencies. */
    class Assembler(private val clockMillis: () -> Long = { System.nanoTime() / 1_000_000 },
                    private val ttlMillis: Long = 120_000, private val capacity: Int = 128) {
        private data class Entry(val total: Int, val started: Long, val chunks: MutableMap<Int, ByteArray> = mutableMapOf())
        private val entries = linkedMapOf<Int, Entry>()
        init { require(ttlMillis > 0 && capacity > 0) }
        fun feed(data: ByteArray): ByteArray? {
            val f = parse(data); val now = clockMillis()
            entries.entries.removeAll { now - it.value.started >= ttlMillis }
            val existing = entries[f.packetId]
            if (existing != null && existing.total != f.total) return null
            if (existing == null && entries.size >= capacity) entries.remove(entries.keys.first())
            val e = existing ?: Entry(f.total, now).also { entries[f.packetId] = it }
            val previous = e.chunks[f.index]
            if (previous != null && !previous.contentEquals(f.payload)) { entries.remove(f.packetId); return null }
            e.chunks[f.index] = f.payload
            if (e.chunks.size != e.total) return null
            entries.remove(f.packetId)
            return (0 until e.total).fold(byteArrayOf()) { result, i -> result + e.chunks.getValue(i) }
        }
    }
}

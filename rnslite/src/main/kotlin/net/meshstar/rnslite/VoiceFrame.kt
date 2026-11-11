package net.meshstar.rnslite

import java.nio.ByteBuffer

class VoiceFrame private constructor(
    val destination: ByteArray, val source: ByteArray, val noteId: Long, val mode: Int,
    val codec2: ByteArray, val part: Part?, val noProof: Boolean, val frame: ByteArray,
) {
    data class Part(val index: Int, val total: Int)
    val seconds: Double get() = codec2.size / (mode / 8.0)
    companion object {
        val magic: ByteArray get() = byteArrayOf(0x4d, 0x53, 0x56, 0x43, 1)
        const val FRAME_MAX = 1400
        const val MAX_CODEC2_BYTES = 1198
        const val MULTIPART = 0x40
        const val NO_PROOF = 0x80
        const val MAX_PARTS = 15
        const val DEFAULT_MAX_PARTS = 4
        val modes = mapOf(3200 to 0, 2400 to 1, 1600 to 2, 1400 to 3, 1300 to 4, 1200 to 5, 700 to 8)
        fun create(from: Identity, to: PublicIdentity, noteId: Long, codec2: ByteArray, mode: Int = 1200,
                   noProof: Boolean = false, part: Part? = null,
                   ephemeralPrivateKey: ByteArray = randomBytes(32), iv: ByteArray = randomBytes(16)): VoiceFrame {
            checkRns(noteId in 0..0xffffffffL && mode in modes, "Voice header")
            checkRns(codec2.size <= MAX_CODEC2_BYTES - if (part == null) 0 else 1, "Voice length")
            if (part != null) checkRns(part.total in 1..MAX_PARTS && part.index in 0 until part.total, "Voice part")
            var code = modes.getValue(mode)
            if (part != null) code = code or MULTIPART
            if (noProof) code = code or NO_PROOF
            var inner = ByteBuffer.allocate(4).putInt(noteId.toInt()).array() + byteArrayOf(code.toByte())
            if (part != null) inner += byteArrayOf(((part.index shl 4) or part.total).toByte())
            inner += codec2
            val dest = to.lxmfAddress; val src = from.lxmfAddress
            val sig = from.sign(dest + src + inner)
            val frame = magic + dest + src + to.encrypt(inner + sig, ephemeralPrivateKey, iv)
            checkRns(frame.size <= FRAME_MAX, "Voice frame too long")
            return VoiceFrame(dest, src, noteId, mode, codec2.copyOf(), part, noProof, frame)
        }
        fun frames(from: Identity, to: PublicIdentity, noteId: Long, codec2: ByteArray, mode: Int = 1200,
                   noProof: Boolean = false, maxParts: Int = DEFAULT_MAX_PARTS): List<VoiceFrame> {
            checkRns(maxParts in 1..MAX_PARTS, "Voice parts limit")
            if (codec2.size <= MAX_CODEC2_BYTES) return listOf(create(from, to, noteId, codec2, mode, noProof))
            val per = MAX_CODEC2_BYTES - 1; val total = (codec2.size + per - 1) / per
            checkRns(total <= maxParts, "Voice note too long")
            return (0 until total).map { i -> create(from, to, noteId, codec2.cut(i * per, minOf(codec2.size, (i + 1) * per)),
                mode, noProof, Part(i, total)) }
        }
        fun receive(frame: ByteArray, me: Identity, recall: (ByteArray) -> PublicIdentity?): VoiceFrame {
            checkRns(frame.size in 133..FRAME_MAX && frame.cut(0, 5).contentEquals(magic), "Voice frame")
            val dest = frame.cut(5, 21); val src = frame.cut(21, 37)
            checkRns(Hash.equal(dest, me.lxmfAddress), "Not for us")
            val plain = me.decrypt(frame.cut(37)); checkRns(plain.size >= 69, "Voice inner length")
            val inner = plain.cut(0, plain.size - 64); val sig = plain.cut(plain.size - 64)
            val sender = recall(src) ?: throw RNSError("Unknown sender")
            checkRns(Hash.equal(sender.lxmfAddress, src) && sender.validate(sig, dest + src + inner), "Invalid signature")
            val code = inner[4].toInt() and 255
            checkRns(code and 0x30 == 0, "Voice flags")
            val mode = modes.entries.firstOrNull { it.value == code and 15 }?.key ?: throw RNSError("Voice codec")
            val part = if (code and MULTIPART != 0) {
                checkRns(inner.size >= 6, "Voice part length")
                val p = inner[5].toInt() and 255; val idx = p shr 4; val total = p and 15
                checkRns(total > 0 && idx < total, "Voice part")
                Part(idx, total)
            } else null
            val codec = inner.cut(if (part == null) 5 else 6)
            checkRns(codec.size <= MAX_CODEC2_BYTES - if (part == null) 0 else 1, "Voice length")
            return VoiceFrame(dest, src, ByteBuffer.wrap(inner.cut(0, 4)).int.toLong() and 0xffffffffL,
                mode, codec, part, code and NO_PROOF != 0, frame.copyOf())
        }
        fun proof(frame: ByteArray, me: Identity): Packet {
            val hash = Hash.sha256(frame)
            return Packet.build(Packet.PROOF, hash.cut(0, 16), me.sign(hash))
        }
        fun validateProof(proof: Packet, frame: ByteArray, peer: PublicIdentity): Boolean =
            Packet.validateProofHash(proof, Hash.sha256(frame), peer)
    }
    class Assembler(private val clockMillis: () -> Long = { System.nanoTime() / 1_000_000 },
                    private val ttlMillis: Long = 120_000, private val capacity: Int = 64) {
        data class Note(val codec2: ByteArray, val lastFrame: ByteArray, val noProof: Boolean)
        private data class Entry(val total: Int, val mode: Int, val noProof: Boolean, val started: Long,
                                 val parts: MutableMap<Int, VoiceFrame> = mutableMapOf())
        private val entries = linkedMapOf<String, Entry>()
        init { require(ttlMillis > 0 && capacity > 0) }
        fun feed(frame: VoiceFrame): Note? {
            val part = frame.part ?: return Note(frame.codec2.copyOf(), frame.frame.copyOf(), frame.noProof)
            val now = clockMillis(); entries.entries.removeAll { now - it.value.started >= ttlMillis }
            val key = frame.source.hex() + ":" + frame.noteId
            val old = entries[key]
            if (old != null && (old.total != part.total || old.mode != frame.mode || old.noProof != frame.noProof)) return null
            if (old == null && entries.size >= capacity) entries.remove(entries.keys.first())
            val e = old ?: Entry(part.total, frame.mode, frame.noProof, now).also { entries[key] = it }
            val previous = e.parts[part.index]
            if (previous != null && !previous.codec2.contentEquals(frame.codec2)) { entries.remove(key); return null }
            e.parts[part.index] = frame
            if (e.parts.size != e.total) return null
            entries.remove(key)
            return Note((0 until e.total).fold(byteArrayOf()) { b, i -> b + e.parts.getValue(i).codec2 },
                e.parts.getValue(e.total - 1).frame.copyOf(), e.noProof)
        }
    }
}

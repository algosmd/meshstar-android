package net.meshstar.voice

import net.meshstar.data.*
import net.meshstar.rnslite.*

/** Persistent parts survive process death and mailbox ACK. Only complete, verified notes get proofs. */
object VoiceInbox {
    const val MAX_BYTES = 4788
    private const val TTL = 7 * 24 * 60 * 60 * 1000L
    fun consume(state: SatState, raw: ByteArray, me: Identity, at: Long, route: Route, carrier: String) {
        if (raw.size !in 133..1400 || !raw.copyOfRange(0, 5).contentEquals(VoiceFrame.magic) ||
            !raw.copyOfRange(5, 21).contentEquals(me.lxmfAddress)) return
        val hash = Hash.sha256(raw).hex()
        val src = raw.copyOfRange(21, 37).hex()
        val contact = state.contacts.firstOrNull { it.address == src }
        val frame = runCatching { VoiceFrame.receive(raw, me) { contact?.identity } }.getOrNull()
        if (frame == null) {
            if (state.messages.none { it.hash == hash }) {
                require(state.messages.size < 10_000)
                state.messages.add(ChatMessage(hash, src, false, "", raw.hex(), route, carrier, at,
                    Delivery.DELIVERED, false, true, voice = VoiceNote(listOf(raw.hex()), "")))
            }
            if (contact == null) {
                require(state.pendingVoice.size < 1000 || hash in state.pendingVoice)
                state.pendingVoice[hash] = raw.hex()
            }
            return
        }
        if (frame.mode != 1200 || frame.codec2.isEmpty() || (frame.part?.total ?: 1) > 4) return
        val complete = state.messages.firstOrNull { !it.outgoing && it.verified && it.voice?.frames?.contains(raw.hex()) == true }
        if (complete != null) {
            if (!complete.voice!!.noProof) enqueue(state, complete.raw.unhex(), src, me, route, carrier)
            return
        }
        state.voiceParts.entries.removeAll { at - it.value.started >= TTL }
        val key = src + ":" + frame.noteId
        val existing = state.voiceParts[key]
        val previous = existing?.frames.orEmpty().map { VoiceFrame.receive(it.unhex(), me) { contact?.identity } }
        if (previous.any { it.mode != frame.mode || it.noProof != frame.noProof || it.part?.total != frame.part?.total }) return
        val duplicate = previous.firstOrNull { it.part?.index == frame.part?.index }
        if (duplicate != null) return // Never reset the expiry on replay; conflicting parts are rejected too.
        val assembler = VoiceFrame.Assembler()
        previous.forEach { assembler.feed(it) }
        val frames = existing?.frames.orEmpty() + raw.hex()
        val result = assembler.feed(frame)
        state.pendingVoice.remove(hash)
        state.messages.removeAll { !it.outgoing && !it.verified && it.hash == hash && it.voice != null }
        if (result == null) {
            require(key in state.voiceParts || state.voiceParts.size < 256)
            state.voiceParts[key] = VoiceParts(existing?.started ?: at, frames)
            return
        }
        if (result.codec2.size !in 6..MAX_BYTES || result.codec2.size % 6 != 0) {
            state.voiceParts.remove(key)
            return
        }
        require(state.messages.size < 10_000)
        val lastHash = Hash.sha256(result.lastFrame).hex()
        if (state.messages.none { it.hash == lastHash }) state.messages.add(ChatMessage(lastHash, src, false, "",
            result.lastFrame.hex(), route, carrier, existing?.started ?: at, Delivery.DELIVERED, true, true,
            voice = VoiceNote(frames, result.codec2.hex(), result.noProof)))
        state.voiceParts.remove(key)
        frames.forEach { val h = Hash.sha256(it.unhex()).hex(); if (h !in state.seen) state.seen.add(h) }
        while (state.seen.size > 5000) state.seen.removeAt(0)
        if (!result.noProof) enqueue(state, result.lastFrame, src, me, route, carrier)
    }
    private fun enqueue(state: SatState, last: ByteArray, peer: String, me: Identity, route: Route, carrier: String) {
        val proof = PendingProof(VoiceFrame.proof(last, me).raw.hex(), peer, route, carrier)
        require(state.proofs.size < 1000 || proof in state.proofs)
        if (proof !in state.proofs) state.proofs.add(proof)
    }
}

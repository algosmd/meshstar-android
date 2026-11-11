package net.meshstar.rnslite

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

// Golden source: meshstar c2c854742af32d95c01090b9193dea327a0b65a5. Keys are public deterministic test fixtures.
class VectorsTest {
    private val v = JsonParser.parseReader(javaClass.getResourceAsStream("/rns_lite.json")!!.reader()).asJsonObject
    private fun section(name: String) = v.getAsJsonObject(name)
    private fun JsonObject.bytes(name: String) = get(name).asString.unhex()
    private val alice = Identity(section("identities").getAsJsonObject("alice").bytes("prv"))
    private val bob = Identity(section("identities").getAsJsonObject("bob").bytes("prv"))
    private val eph = section("fixed").bytes("ephemeral_x25519_prv")
    private val iv = section("fixed").bytes("iv")
    private fun message(): LXMessage = section("lxmf").let {
        LXMessage.create(alice, bob, it.bytes("content"), it["timestamp"].asDouble, it.bytes("title"))
    }
    private fun receiveVoice(raw: ByteArray) = VoiceFrame.receive(raw, bob) { if (it.contentEquals(alice.lxmfAddress)) alice else null }
    @Test fun fixtureProvenanceChecksum() {
        val bytes = javaClass.getResourceAsStream("/rns_lite.json")!!.use { it.readBytes() }
        val expected = javaClass.getResourceAsStream("/rns_lite.sha256")!!.reader().use { it.readText().substringBefore(" ") }
        assertEquals(expected, Hash.sha256(bytes).hex())
    }
    @Test fun identitiesAndNameHash() {
        assertEquals(1, v["version"].asInt)
        assertEquals("1.5.7", v["rns"].asString)
        assertArrayEquals(v["name_hash_lxmf_delivery"].asString.unhex(), Hash.sha256("lxmf.delivery".toByteArray()).cut(0, 10))
        for ((name, id) in listOf("alice" to alice, "bob" to bob)) {
            val o = section("identities").getAsJsonObject(name)
            assertArrayEquals(o.bytes("pub"), id.publicKey)
            assertArrayEquals(o.bytes("hash"), id.hash)
            assertArrayEquals(o.bytes("lxmf_delivery"), id.lxmfAddress)
            assertArrayEquals(o.bytes("prv"), id.exportPrivateKey())
        }
    }
    @Test fun hkdfAndEncryption() {
        val h = section("hkdf"); val e = section("encrypt")
        assertArrayEquals(h.bytes("derived"), Hash.hkdf(h.bytes("shared"), h.bytes("salt")))
        assertArrayEquals(h.bytes("derived"), e.bytes("hmac_key") + e.bytes("aes_key"))
        assertArrayEquals(e.bytes("token"), bob.encrypt(e.bytes("plaintext"), eph, iv))
        assertArrayEquals(e.bytes("plaintext"), bob.decrypt(e.bytes("token")))
        assertArrayEquals(e.bytes("token").cut(32), Token(h.bytes("derived")).encrypt(e.bytes("plaintext"), iv))
    }
    @Test fun signature() {
        val s = section("sign")
        assertArrayEquals(s.bytes("signature"), alice.sign(s.bytes("message")))
        assertTrue(alice.validate(s.bytes("signature"), s.bytes("message")))
        assertFalse(bob.validate(s.bytes("signature"), s.bytes("message")))
    }
    @Test fun lxmfAndPacket() {
        val l = section("lxmf"); val m = message(); val p = m.packet(bob, eph, iv)
        assertArrayEquals(l.bytes("payload_msgpack"), m.payload)
        assertArrayEquals(l.bytes("message_hash"), m.hash)
        assertArrayEquals(l.bytes("signature"), m.signature)
        assertArrayEquals(l.bytes("packed"), m.packed)
        assertArrayEquals(l.bytes("packet_plain"), m.packetPlain)
        assertArrayEquals(l.bytes("packet_raw"), p.raw)
        assertArrayEquals(l.bytes("packet_hash"), p.hash)
        val incoming = LXMessage.receive(Packet(l.bytes("packet_raw")), bob) { alice }
        assertArrayEquals(m.packed, incoming.packed)
        assertArrayEquals(l.bytes("proof_raw"), p.prove(bob).raw)
        assertArrayEquals(l.bytes("proof_dest"), p.prove(bob).destination)
        assertArrayEquals(l.bytes("proof_signature"), p.prove(bob).payload)
        assertTrue(p.validateProof(Packet(l.bytes("proof_raw")), bob))
        assertFalse(p.validateProof(Packet(l.bytes("proof_raw")), alice))
    }
    @Test fun relayHeaderPreservesPacketHash() {
        val via = section("lxmf").getAsJsonObject("via_transport")
        val p = Packet(section("lxmf").bytes("packet_raw")).via(via.bytes("transport_id"))
        assertArrayEquals(via.bytes("packet_raw"), p.raw)
        assertArrayEquals(via.bytes("packet_hash"), p.hash)
        assertArrayEquals(via.bytes("transport_id"), p.transportId)
        assertArrayEquals(message().packed, LXMessage.receive(p, bob) { alice }.packed)
    }
    @Test fun rnsi() {
        val r = section("rnsi"); val p = Packet(section("lxmf").bytes("packet_raw"))
        assertArrayEquals(r.bytes("frame"), SatFrame.encode(p))
        val decoded = SatFrame.decode(r.bytes("frame"))
        assertArrayEquals(p.raw, decoded.raw)
        assertEquals(r["ptype"].asInt, decoded.type)
        assertArrayEquals(r.bytes("dest"), decoded.destination)
        assertArrayEquals(r.bytes("phash"), decoded.truncatedHash)
        assertEquals(SatFrame.Kind.RNS, SatFrame.classify(r.bytes("frame")))
        assertEquals(SatFrame.Kind.VOICE, SatFrame.classify(section("voice").bytes("frame")))
        assertEquals(SatFrame.Kind.TEXT, SatFrame.classify("example".toByteArray()))
    }
    @Test fun meshcoreBothMtuSizesAndOutOfOrder() {
        val m = section("meshcore"); val packet = section("lxmf").bytes("packet_raw")
        assertEquals(m["magic_ver"].asInt, MeshCoreFrag.MAGIC_VERSION)
        for (size in listOf(159, 68)) {
            val expected = m.getAsJsonArray("frags_$size").map { it.asString.unhex() }
            val actual = MeshCoreFrag.split(packet, m["pkt_id"].asInt, size)
            assertEquals(expected.size, actual.size)
            expected.zip(actual).forEach { (a, b) -> assertArrayEquals(a, b) }
            val assembler = MeshCoreFrag.Assembler()
            assertNull(assembler.feed(expected.last()))
            assertNull(assembler.feed(expected.last()))
            var result: ByteArray? = null
            expected.dropLast(1).reversed().forEach { result = assembler.feed(it) }
            assertArrayEquals(packet, result)
        }
    }
    @Test fun textcodecAllSamples() {
        val t = section("textcodec")
        assertArrayEquals(t.bytes("dict_v1_cp1251"), TextCodec.dictionary(t["dict_version"].asInt))
        for (sample in t.getAsJsonArray("samples")) {
            val s = sample.asJsonObject; val encoded = s.bytes("encoded")
            assertEquals(s["tag"].asInt, encoded[0].toInt() and 255)
            assertEquals(s["text"].asString, TextCodec.decode(encoded))
        }
    }
    @Test fun mailboxAddressesBothDirectionsAndUtcWindow() {
        val m = section("mailbox"); val day = LocalDate.parse(m["day"].asString)
        assertEquals("meshsat-mailbox-v1", m["salt"].asString)
        assertArrayEquals(m.bytes("shared"), alice.sharedKey(bob))
        assertArrayEquals(m.bytes("shared"), bob.sharedKey(alice))
        assertArrayEquals(m.bytes("to_bob"), Mailbox.address(alice, bob, bob.lxmfAddress, day))
        assertArrayEquals(m.bytes("to_bob"), Mailbox.address(bob, alice, bob.lxmfAddress, day))
        assertArrayEquals(m.bytes("to_alice"), Mailbox.address(alice, bob, alice.lxmfAddress, day))
        assertArrayEquals(m.bytes("public_bob"), Mailbox.publicAddress(bob.lxmfAddress))
        val expected = m.getAsJsonArray("bob_poll_2026_11_04").map { it.asString.unhex() }
        val actual = Mailbox.pickupAddresses(bob, alice, Instant.parse("2026-11-04T23:59:59Z"))
        expected.zip(actual).forEach { (a, b) -> assertArrayEquals(a, b) }
    }
    @Test fun mailboxProofOfWork() {
        val s = section("mailbox").getAsJsonObject("stamp")
        val box = s.bytes("mailbox"); val data = s.bytes("data"); val nonce = s.bytes("nonce"); val bits = s["bits"].asInt
        assertArrayEquals(s.bytes("digest"), MailboxStamp.digest(box, data, nonce))
        assertArrayEquals(nonce, MailboxStamp.solve(box, data, bits))
        assertTrue(MailboxStamp.verify(box, data, nonce, bits))
        assertFalse(MailboxStamp.verify(box, data + byteArrayOf(1), nonce, bits))
    }
    @Test fun voiceSingleFrameByteExact() {
        val v = section("voice")
        val frame = VoiceFrame.create(alice, bob, v["note_id"].asLong, v.bytes("codec2"), v["mode"].asInt,
            ephemeralPrivateKey = eph, iv = iv)
        assertEquals(v["max_c2_bytes"].asInt, VoiceFrame.MAX_CODEC2_BYTES)
        assertArrayEquals(v.bytes("frame"), frame.frame)
        assertArrayEquals(v.bytes("signature"), alice.sign(bob.lxmfAddress + alice.lxmfAddress + v.bytes("inner_plain")))
        val incoming = receiveVoice(v.bytes("frame"))
        assertArrayEquals(v.bytes("codec2"), incoming.codec2)
        assertEquals(frame.noteId, incoming.noteId); assertEquals(frame.mode, incoming.mode)
        assertFalse(incoming.noProof); assertNull(incoming.part)
    }
    @Test fun voiceMultipartAndProof() {
        val v = section("voice_multipart"); val raw = v.getAsJsonArray("frames").map { it.asString.unhex() }
        assertEquals(v["flag_multipart"].asInt, VoiceFrame.MULTIPART)
        assertEquals(v["flag_no_proof"].asInt, VoiceFrame.NO_PROOF)
        assertEquals(v["frame_max"].asInt, VoiceFrame.FRAME_MAX)
        assertEquals(v["max_parts"].asInt, VoiceFrame.DEFAULT_MAX_PARTS)
        val frames = raw.map { receiveVoice(it) }
        frames.forEachIndexed { index, f ->
            val part = v.getAsJsonArray("parts")[index].asJsonArray
            assertEquals(VoiceFrame.Part(part[0].asInt, part[1].asInt), f.part)
            assertTrue(f.frame.size <= VoiceFrame.FRAME_MAX)
            assertArrayEquals(f.frame, VoiceFrame.create(alice, bob, f.noteId, f.codec2, f.mode,
                part = f.part, ephemeralPrivateKey = eph, iv = iv).frame)
        }
        val assembler = VoiceFrame.Assembler()
        assertNull(assembler.feed(frames[2])); assertNull(assembler.feed(frames[0])); assertNull(assembler.feed(frames[0]))
        val note = requireNotNull(assembler.feed(frames[1]))
        assertArrayEquals(v.bytes("codec2"), note.codec2)
        assertArrayEquals(raw.last(), note.lastFrame)
        assertArrayEquals(v.bytes("last_frame_hash"), Hash.sha256(note.lastFrame))
        val proof = VoiceFrame.proof(note.lastFrame, bob)
        assertArrayEquals(v.bytes("proof_raw"), proof.raw); assertArrayEquals(v.bytes("proof_signature"), proof.payload)
        assertTrue(VoiceFrame.validateProof(proof, note.lastFrame, bob))
        assertFalse(VoiceFrame.validateProof(proof, raw.first(), bob))
        val noProof = receiveVoice(v.bytes("no_proof_frame"))
        assertTrue(noProof.noProof)
        assertArrayEquals(noProof.frame, VoiceFrame.create(alice, bob, noProof.noteId, noProof.codec2, noProof.mode,
            noProof = true, ephemeralPrivateKey = eph, iv = iv).frame)
        val split = VoiceFrame.frames(alice, bob, v["note_id"].asLong, v.bytes("codec2"))
        assertEquals(frames.map { it.part }, split.map { it.part })
        frames.zip(split).forEach { (a, b) -> assertArrayEquals(a.codec2, b.codec2) }
    }
    @Test fun codec2FixtureCanBeCarriedLosslessly() {
        // Codec2 native encoding is A6. A1 treats its golden output as opaque bytes, never audio-decodes it.
        val c = section("codec2")
        assertEquals(1200, c["mode"].asInt); assertEquals(8000, c["rate"].asInt)
        assertEquals(16000, c.bytes("pcm16_le_hex").size); assertEquals(150, c.bytes("codec2").size)
        val frame = VoiceFrame.create(alice, bob, 1, c.bytes("codec2"))
        assertArrayEquals(c.bytes("codec2"), receiveVoice(frame.frame).codec2)
    }
}

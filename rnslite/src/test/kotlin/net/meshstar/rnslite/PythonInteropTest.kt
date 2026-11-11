package net.meshstar.rnslite

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.TimeUnit

class PythonInteropTest {
    @Test fun livePythonReferenceAcceptsKotlinAndKotlinAcceptsPython() {
        // Fresh random test identities and randomized encryption prevent a fixture-only implementation passing.
        val alice = Identity(); val bob = Identity()
        val content = "kotlin to python: synthetic payload".toByteArray()
        val message = LXMessage.create(alice, bob, content, 1793029366.0, "synthetic".toByteArray(),
            MsgPack.MapValue(listOf(1L to "fixture")))
        val packet = message.packet(bob)
        val input = JsonObject().apply {
            addProperty("alice_private", alice.exportPrivateKey().hex()); addProperty("bob_private", bob.exportPrivateKey().hex())
            addProperty("packet", packet.raw.hex()); addProperty("packet_hash", packet.hash.hex())
            addProperty("content", content.hex()); addProperty("proof", packet.prove(bob).raw.hex())
        }
        val dir = Files.createTempDirectory("rnslite-interop-").toFile()
        try {
            val script = dir.resolve("oracle.py")
            script.writeBytes(javaClass.getResourceAsStream("/python_interop.py")!!.use { it.readBytes() })
            val request = dir.resolve("request.json").also { it.writeText(input.toString()) }
            val response = dir.resolve("response.json")
            val log = dir.resolve("oracle.log")
            val python = System.getenv("RNSLITE_PYTHON") ?: "python3"
            val process = ProcessBuilder(python, script.path, request.path, response.path).redirectErrorStream(true).redirectOutput(log).start()
            if (!process.waitFor(30, TimeUnit.SECONDS)) { process.destroyForcibly(); fail("Python reference timed out") }
            assertEquals("Python reference failed: ${log.readText()}", 0, process.exitValue())
            val result = JsonParser.parseString(response.readText()).asJsonObject
            assertEquals("1.5.7", result["rns_version"].asString)
            assertEquals("0.9.2", result["lxmf_version"].asString)
            val incoming = Packet(result["packet"].asString.unhex())
            assertArrayEquals(result["packet_hash"].asString.unhex(), incoming.hash)
            val received = LXMessage.receive(incoming, alice) { if (it.contentEquals(bob.lxmfAddress)) bob else null }
            assertArrayEquals(result["packed"].asString.unhex(), received.packed)
            assertArrayEquals(result["content"].asString.unhex(), received.content)
            assertEquals(MsgPack.MapValue(listOf(1L to "fixture")), received.fields)
            assertTrue(packet.validateProof(Packet(result["proof"].asString.unhex()), bob))
        } finally { dir.deleteRecursively() }
    }
}

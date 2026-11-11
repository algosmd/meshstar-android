package net.meshstar.mailbox

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import net.meshstar.box.CarrierHttp
import net.meshstar.box.HttpFailure
import net.meshstar.rnslite.*
import java.util.Base64

data class MailItem(val id: Long, val mailbox: String, val kind: String, val raw: ByteArray)
data class Pickup(val items: List<MailItem>, val next: Long)
class MailboxTransport(val url: String, private val http: CarrierHttp = CarrierHttp()) {
    suspend fun drop(raw: ByteArray, mailbox: ByteArray, kind: String = "packet"): Long {
        require(mailbox.size == 16 && kind in listOf("packet", "voice") && raw.size in 1..if (kind == "voice") 1400 else 500)
        val body = JsonObject().apply {
            addProperty("mailbox", mailbox.hex()); addProperty("kind", kind)
            addProperty("data", Base64.getEncoder().encodeToString(raw))
        }
        val result = try { http.post(url, "v1/drop", body) } catch (error: HttpFailure) {
            if (error.powBits == null) throw error
            val nonce = withContext(Dispatchers.Default) {
                val context = currentCoroutineContext()
                MailboxStamp.solve(mailbox, raw, error.powBits) { !context.isActive }
            }
            currentCoroutineContext().ensureActive()
            body.addProperty("stamp", nonce.hex())
            http.post(url, "v1/drop", body) // At most one challenge retry.
        }
        return JsonParser.parseString(result).asJsonObject["id"].asLong.also { require(it > 0) }
    }
    suspend fun pickup(addresses: List<String>, since: Long, wait: Int = 25): Pickup {
        require(addresses.size in 1..256 && addresses.all { it.unhex().size == 16 } && since >= 0 && wait in 0..25)
        val body = JsonObject().apply { add("mailboxes", strings(addresses)); addProperty("since", since); addProperty("wait", wait) }
        val result = JsonParser.parseString(http.post(url, "v1/pickup", body)).asJsonObject
        val next = result["next"].asLong
        val items = result.getAsJsonArray("items").map {
            val item = it.asJsonObject; val kind = item["kind"].asString; val data = item["data"].asString
            require(kind in listOf("packet", "voice") && data.length <= 1872 && item["ts"].asDouble.isFinite())
            val raw = Base64.getDecoder().decode(data)
            require(raw.size in 1..if (kind == "voice") 1400 else 500)
            MailItem(item["id"].asLong, item["mailbox"].asString, kind, raw)
        }
        require(items.size <= 200 && items.map { it.id }.distinct().size == items.size &&
            items.all { it.id > since && it.id <= next && it.mailbox in addresses } && next == (items.maxOfOrNull { it.id } ?: since)) { "Invalid mailbox response" }
        return Pickup(items, next)
    }
    suspend fun ack(ids: List<Long>, addresses: List<String>) {
        require(ids.size <= 1000 && addresses.size <= 256)
        val body = JsonObject().apply { add("ids", JsonArray().apply { ids.forEach { add(it) } }); add("mailboxes", strings(addresses)) }
        val result = JsonParser.parseString(http.post(url, "v1/ack", body)).asJsonObject
        require(result["deleted"].asInt in 0..ids.size) { "Invalid ACK response" }
    }
    private fun strings(values: List<String>) = JsonArray().apply { values.forEach { add(it) } }
}

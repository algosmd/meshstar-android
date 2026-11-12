package net.meshstar.box

import com.google.gson.JsonParser
import com.google.gson.JsonObject
import net.meshstar.link.Contact
import net.meshstar.rnslite.*

data class BoxConfig(val url: String, val token: String, val address: String = "")
data class BoxStatus(val address: String, val pointOnline: Boolean)
data class SentRow(val id: Long, val hash: String, val status: String)
data class InboxRow(val id: Long, val raw: String, val kind: String)
class BoxTransport(val config: BoxConfig, private val http: CarrierHttp = CarrierHttp()) {
    suspend fun status(): String = statusDetails().address
    suspend fun statusDetails(): BoxStatus {
        val root = JsonParser.parseString(http.request(config.url, "api/status", config.token)).asJsonObject
        val address = root["addr"].asString.lowercase().also { require(it.unhex().size == 16) { "Invalid box address" } }
        val online = runCatching { root.getAsJsonObject("link")?.get("online")?.asBoolean == true }.getOrDefault(false)
        return BoxStatus(address, online)
    }
    suspend fun pair(me: Contact): Contact {
        val root = JsonParser.parseString(http.request(config.url, "api/pair", config.token)).asJsonObject
        val box = Contact.parse(root["qr"].asString)
        require(box.address == config.address) { "Box QR does not match status" }
        http.request(config.url, "api/pair", config.token,
            JsonObject().apply { addProperty("qr", me.qr) }.toString().toByteArray())
        return box
    }
    suspend fun send(packet: Packet, noProof: Boolean = false): Long {
        val root = JsonParser.parseString(http.request(config.url, "api/rns/packet", config.token, packet.raw, false,
            mapOf("no_proof" to noProof.toString()))).asJsonObject
        require(root["hash"].asString == packet.hash.hex() && root["dest"].asString == packet.destination.hex()) { "Invalid box receipt" }
        return root["id"].asLong.also { require(it > 0) }
    }
    suspend fun sent(since: Long): List<SentRow> = JsonParser.parseString(http.request(config.url, "api/rns/sent", config.token,
        query = mapOf("since" to since.toString(), "limit" to "500"))).asJsonArray.map {
        val row = it.asJsonObject
        SentRow(row["id"].asLong, row["pkt_hash"]?.takeUnless { h -> h.isJsonNull }?.asString.orEmpty(), row["status"].asString)
    }.also { rows -> require(rows.size <= 500 && rows.all { it.id > since }) }
    suspend fun inbox(since: Long): List<InboxRow> = JsonParser.parseString(http.request(config.url, "api/rns/inbox", config.token,
        query = mapOf("since" to since.toString(), "limit" to "500"))).asJsonArray.map {
        val row = it.asJsonObject
        InboxRow(row["id"].asLong, row["raw"].asString, row["kind"].asString)
    }.also { rows -> require(rows.size <= 500 && rows.all { it.id > since }) }
}

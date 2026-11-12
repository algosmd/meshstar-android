package net.meshstar.box

import com.google.gson.JsonParser
import com.google.gson.JsonObject
import net.meshstar.link.Contact
import net.meshstar.rnslite.*

data class BoxConfig(val url: String, val token: String, val address: String = "", val transportId: String? = null)
data class BoxStatus(val address: String, val pointOnline: Boolean, val transportId: String? = null)
data class SentRow(val id: Long, val hash: String, val status: String)
data class ChannelHistoryRow(val ts:Double,val timestamp:Long,val text:String,val snr:Double?,val hops:Int?)
data class InboxRow(val id: Long, val raw: String, val kind: String)
class BoxTransport(val config: BoxConfig, private val http: CarrierHttp = CarrierHttp()) {
    suspend fun status(): String = statusDetails().address
    suspend fun statusDetails(): BoxStatus {
        val root = JsonParser.parseString(http.request(config.url, "api/status", config.token)).asJsonObject
        val address = root["addr"].asString.lowercase().also { require(it.unhex().size == 16) { "Invalid box address" } }
        val online = runCatching { root.getAsJsonObject("link")?.get("online")?.asBoolean == true }.getOrDefault(false)
        val transportId = root.getAsJsonObject("transport")?.get("id")?.takeUnless { it.isJsonNull }?.asString?.lowercase()
        require(transportId == null || transportId.unhex().size == 16) { "Invalid transport identity" }
        return BoxStatus(address, online, transportId)
    }
    suspend fun pair(me: Contact): Contact = pairDetails(me).first
    suspend fun pairDetails(me: Contact): Pair<Contact,String?> {
        val root = JsonParser.parseString(http.request(config.url, "api/pair", config.token)).asJsonObject
        val box = Contact.parse(root["qr"].asString)
        require(box.address == config.address) { "Box QR does not match status" }
        http.request(config.url, "api/pair", config.token,
            JsonObject().apply { addProperty("qr", me.qr) }.toString().toByteArray())
        val transportId = root["transport_id"]?.takeUnless { it.isJsonNull }?.asString?.lowercase()
        require(transportId == null || transportId.unhex().size == 16) { "Invalid transport identity" }
        return box to transportId
    }
    suspend fun channels(): List<Pair<Int,String>> = JsonParser.parseString(http.request(config.url,"api/channels",config.token)).asJsonArray.map {
        val row=it.asJsonObject; row["idx"].asInt to row["name"].asString
    }.also { require(it.size<=40 && it.all { r -> r.first in 0..39 && r.second.length<=128 }) }
    suspend fun channelHistory(index:Int,since:Double): List<ChannelHistoryRow> {
        require(index in 0..39 && since.isFinite() && since>=0)
        return JsonParser.parseString(http.request(config.url,"api/channels/$index/history",config.token,
            query=mapOf("since" to since.toString(),"limit" to "200"))).asJsonArray.map {
            val r=it.asJsonObject
            ChannelHistoryRow(r["ts"].asDouble,r["sender_timestamp"].asLong,r["text"].asString,
                r["snr"]?.takeUnless { it.isJsonNull }?.asDouble,r["path_len"]?.takeUnless { it.isJsonNull }?.asInt)
        }.also { require(it.size<=200 && it.all { r->r.ts.isFinite()&&r.ts>=since&&r.text.length<=1024&&(r.snr==null||r.snr.isFinite()) }) }
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

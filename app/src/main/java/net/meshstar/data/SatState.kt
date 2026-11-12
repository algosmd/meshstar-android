package net.meshstar.data

import com.google.gson.Gson
import net.meshstar.box.BoxConfig
import net.meshstar.identity.EncryptedFiles
import net.meshstar.link.Contact

enum class Route { SATELLITE, INTERNET, LORA, RELAY }
enum class Delivery { QUEUED, SENT, DELIVERED }
data class ChatMessage(
    val hash: String, val peer: String, val outgoing: Boolean, val text: String,
    val raw: String, val route: Route, val carrier: String, val at: Long,
    var delivery: Delivery = Delivery.QUEUED, val verified: Boolean = true,
    var submitted: Boolean = false, var serverId: Long = 0,
    var viaPoint: Boolean = false,
    val relayTransportId: String? = null,
    var radioSnr: Double? = null, var radioHops: Int? = null,
)
data class PendingProof(val raw: String, val peer: String, val route: Route, val carrier: String)
data class BoxCursor(var inbox: Long = 0, var sent: Long = 0)
data class ChannelText(val id: String, val channel: String, val text: String, val timestamp: Long,
    val receivedAt: Long, val fromBox: Boolean = false, val snr: Double? = null, val hops: Int? = null,
    val outgoing: Boolean = false, val directKey: String? = null)
data class SatState(
    var name: String = "MeshStar", var box: BoxConfig? = null, var internet: String = "",
    val contacts: MutableList<Contact> = mutableListOf(), val messages: MutableList<ChatMessage> = mutableListOf(),
    val proofs: MutableList<PendingProof> = mutableListOf(),
    val boxCursors: MutableMap<String, BoxCursor> = mutableMapOf(),
    val mailCursors: MutableMap<String, Long> = mutableMapOf(),
    val seen: MutableList<String> = mutableListOf(),
    val pendingVoice: MutableMap<String, String> = mutableMapOf(),
    val verifiedKeys: MutableMap<String, String> = mutableMapOf(),
    val bluetoothLinks: MutableMap<String, String> = mutableMapOf(),
    val readThrough: MutableMap<String, Long> = mutableMapOf(),
    var readTrackingInitialized: Boolean = false,
    var node: net.meshstar.meshcore.NodeConfiguration? = null,
    val nativeContacts: MutableMap<String,String> = mutableMapOf(),
    val nativeChats: MutableSet<String> = mutableSetOf(),
    val channelNames: MutableMap<Int,String> = mutableMapOf(),
    val channelTexts: MutableList<ChannelText> = mutableListOf(),
    val channelCursors: MutableMap<String,Double> = mutableMapOf(),
    val radioObservations: MutableMap<String,MutableMap<String,Long>> = mutableMapOf(),

)
interface StateStore { fun load(): SatState; fun save(state: SatState) }
class EncryptedStateStore(private val files: EncryptedFiles) : StateStore {
    private val gson = Gson()
    override fun load(): SatState = files.read("state")?.let { gson.fromJson(it.toString(Charsets.UTF_8), SatState::class.java) } ?: SatState()
    override fun save(state: SatState) = files.write("state", gson.toJson(state).toByteArray())
}

package net.meshstar.data

import com.google.gson.Gson
import net.meshstar.box.BoxConfig
import net.meshstar.identity.EncryptedFiles
import net.meshstar.link.Contact

enum class Route { SATELLITE, INTERNET }
enum class Delivery { QUEUED, SENT, DELIVERED }
data class ChatMessage(
    val hash: String, val peer: String, val outgoing: Boolean, val text: String,
    val raw: String, val route: Route, val carrier: String, val at: Long,
    var delivery: Delivery = Delivery.QUEUED, val verified: Boolean = true,
    var submitted: Boolean = false, var serverId: Long = 0,
    var viaPoint: Boolean = false,
)
data class PendingProof(val raw: String, val peer: String, val route: Route, val carrier: String)
data class BoxCursor(var inbox: Long = 0, var sent: Long = 0)
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

)
interface StateStore { fun load(): SatState; fun save(state: SatState) }
class EncryptedStateStore(private val files: EncryptedFiles) : StateStore {
    private val gson = Gson()
    override fun load(): SatState = files.read("state")?.let { gson.fromJson(it.toString(Charsets.UTF_8), SatState::class.java) } ?: SatState()
    override fun save(state: SatState) = files.write("state", gson.toJson(state).toByteArray())
}

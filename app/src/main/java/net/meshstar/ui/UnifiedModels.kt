package net.meshstar.ui

import net.meshstar.data.*

enum class ChatTab(val title: T) { BLUETOOTH(T.bluetooth), LORA(T.lora), SATELLITE(T.satellite) }
enum class PathChoice(val title: T) { AUTO(T.auto), LORA(T.lora), RELAY(T.relay), SATELLITE(T.satellite), INTERNET(T.internet) }
data class UiPeer(val id: String, val name: String, val fingerprint: String = "", val rns: String? = null,
                  val ble: String? = null, val bleFingerprint: String? = null, val nearby: Boolean = false,
                  val verified: Boolean = false, val preview: String = "", val unread: Int = 0,
                  val paths: Set<ChatTab> = emptySet(), val at: Long = 0, val qr: String = "")
data class UiMessage(val id: String, val name: String, val text: String, val at: Long, val outgoing: Boolean,
                     val route: Route? = null, val viaPoint: Boolean = false, val delivery: Delivery = Delivery.SENT,
                     val signature: Boolean? = null, val peer: String = "", val showDelivery: Boolean = route != null)
data class UiSnapshot(val name: String = "MeshStar", val peers: List<UiPeer> = emptyList(),
    val messages: Map<String, List<UiMessage>> = emptyMap(), val publicMessages: List<UiMessage> = emptyList(),
    val unreadPublic: Int = 0, val myQr: String = "", val myFingerprint: String = "", val myAddress: String = "",
    val boxUrl: String = "", val boxToken: String = "", val internet: String = "", val pointOnline: Boolean = false,
    val boxStatus: String = "Box not configured", val mailStatus: String = "Internet server disabled",
    val bleReady: Boolean = false, val unreadByTab: Map<ChatTab, Int> = emptyMap())
sealed interface UiAction {
    data class Send(val peer: String, val text: String, val path: PathChoice): UiAction
    data class Read(val peer: String): UiAction
    data class Import(val text: String): UiAction
    data class Verify(val peer: String, val value: Boolean): UiAction
    data class Link(val blePeer: String, val rnsPeer: String): UiAction
    data class SaveBox(val url: String, val token: String): UiAction
    data class SaveInternet(val url: String): UiAction
    data class Rename(val name: String): UiAction
    data class Language(val value: String): UiAction
    data class Appearance(val value: String): UiAction
    data object DisableBox: UiAction
    data object BluetoothSetup: UiAction
}
/** Auto selects only implemented, configured paths. A4 never pretends a LoRa packet was sent. */
fun resolvePath(choice: PathChoice, bluetooth: Boolean, internet: Boolean, satellite: Boolean): String? = when (choice) {
    PathChoice.AUTO -> when { bluetooth -> "bluetooth"; internet -> "internet"; satellite -> "satellite"; else -> null }
    PathChoice.INTERNET -> if (internet) "internet" else null
    PathChoice.SATELLITE -> if (satellite) "satellite" else null
    PathChoice.LORA, PathChoice.RELAY -> null
}
/** Fingerprint-linked identities only: never merge two strangers by their nickname. */
fun unifiedPeers(rns: List<UiPeer>, bluetooth: List<UiPeer>, links: Map<String, String>): List<UiPeer> {
    val result = rns.associateBy { it.id }.toMutableMap()
    for (peer in bluetooth) {
        val target = peer.bleFingerprint?.let { links[it] }?.let { "rns:$it" }
        val existing = result[target]
        if (existing != null) result[existing.id] = existing.copy(ble = peer.ble, bleFingerprint = peer.bleFingerprint,
            nearby = peer.nearby, paths = existing.paths + peer.paths, unread = existing.unread + peer.unread,
            at = maxOf(existing.at, peer.at), preview = if (peer.at > existing.at) peer.preview else existing.preview)
        else result[peer.id] = peer
    }
    return result.values.sortedWith(compareByDescending<UiPeer> { it.at }.thenBy { it.name.lowercase() })
}

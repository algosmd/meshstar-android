package net.meshstar.link

import net.meshstar.rnslite.*
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Base64

/** RNS contacts use the existing iOS/box payload, independent of the BLE Noise identity. */
data class Contact(val address: String, val key: String, val name: String) {
    val identity: PublicIdentity get() = PublicIdentity(Base64.getDecoder().decode(key))
    val fingerprint: String get() = identity.fingerprint
    val qr: String get() = "meshsat1:$address:$key:$name"
    val link: String get() = "meshstar://contact?qr=" + URLEncoder.encode(qr, "UTF-8")
    companion object {
        fun of(identity: PublicIdentity, name: String) = Contact(identity.lxmfAddress.hex(),
            Base64.getEncoder().encodeToString(identity.publicKey), name.trim().take(80).ifEmpty { identity.lxmfAddress.hex().take(8) })
        fun parse(input: String): Contact {
            var text = input.trim()
            require(text.toByteArray().size <= 4096) { "QR is too large" }
            if (!text.startsWith("meshsat1:")) {
                val uri = ContactLink.uri(text)
                require(uri.host == "contact") { "Not an RNS contact QR" }
                text = ContactLink.query(uri)["qr"] ?: error("Missing contact payload")
            }
            require(text.startsWith("meshsat1:")) { "Not an RNS contact QR" }
            val parts = text.removePrefix("meshsat1:").split(':', limit = 3)
            require(parts.size >= 2) { "Incomplete contact" }
            val identity = PublicIdentity(Base64.getDecoder().decode(parts[1]))
            require(parts[0].lowercase() == identity.lxmfAddress.hex()) { "Address does not match public key" }
            return of(identity, parts.getOrElse(2) { "" })
        }
    }
}

object ContactLink {
    fun uri(text: String): URI = URI(text).also {
        require(text.length <= 4096 && it.scheme?.lowercase() in listOf("meshstar", "meshsat") &&
            it.userInfo == null && it.fragment == null && it.port == -1 && it.path.orEmpty().isEmpty()) { "Invalid MeshStar link" }
    }
    fun query(uri: URI): Map<String, String> {
        val values = uri.rawQuery.orEmpty().split('&').filter { it.isNotEmpty() }.map {
            val p = it.split('=', limit = 2)
            URLDecoder.decode(p[0], "UTF-8") to URLDecoder.decode(p.getOrElse(1) { "" }, "UTF-8")
        }
        require(values.map { it.first }.distinct().size == values.size) { "Duplicate QR field" }
        return values.toMap()
    }
    /** Explicit import only. A deep link never sends credentials or starts pairing by itself. */
    fun box(text: String): Pair<String, String> {
        val uri = uri(text.trim()); require(uri.host == "box") { "Not a box setup QR" }
        val query = query(uri)
        return (query["url"] ?: error("Missing box URL")) to (query["token"] ?: error("Missing box token"))
    }
}

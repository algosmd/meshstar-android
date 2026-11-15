package net.meshstar.box

import okhttp3.HttpUrl
import java.io.IOException

class CleartextEndpointRejected : IOException("HTTPS required for public servers")

/** Literal addresses only: DNS names do not inherit a private-address exemption. */
object CarrierNetworkPolicy {
    fun requireAllowed(url: HttpUrl) {
        if (!url.isHttps && !allowsCleartextHost(url.host)) throw CleartextEndpointRejected()
    }

    fun allowsCleartextHost(host: String): Boolean {
        if (host == "localhost" || host == "127.0.0.1" || host == "::1") return true
        val parts = host.split('.')
        if (parts.size != 4) return false
        val octets = parts.map {
            if (it.isEmpty() || it.length > 3 || it.any { c -> c !in '0'..'9' } ||
                (it.length > 1 && it.startsWith('0'))) return false
            it.toInt().also { n -> if (n !in 0..255) return false }
        }
        return octets[0] == 10 ||
            (octets[0] == 172 && octets[1] in 16..31) ||
            (octets[0] == 192 && octets[1] == 168) ||
            (octets[0] == 100 && octets[1] in 64..127)
    }
}

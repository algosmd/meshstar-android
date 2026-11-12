package net.meshstar.link

import android.net.Uri

/** Scheme aliases change navigation only, never the signed QR canonical bytes. */
object AppLinks {
    fun accepts(uri: Uri): Boolean = uri.scheme?.lowercase() in setOf("meshstar", "meshsat")
    fun isVerification(uri: Uri): Boolean =
        (accepts(uri) || uri.scheme == "bitchat") && uri.host == "verify"
    fun navigation(uri: Uri): Boolean =
        (accepts(uri) && uri.host in setOf("verify", "user", "share")) || isVerification(uri)
    fun safe(uri: Uri): Boolean = uri.toString().length <= 4096 && uri.userInfo == null && uri.fragment == null && uri.port == -1
    fun peer(uri: Uri): String? = (uri.lastPathSegment ?: uri.getQueryParameter("id"))
        ?.takeIf { it.matches(Regex("[A-Za-z0-9:_-]{1,128}")) }
}

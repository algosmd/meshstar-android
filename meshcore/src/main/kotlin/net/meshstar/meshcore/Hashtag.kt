package net.meshstar.meshcore

import java.security.MessageDigest

/** Standard MeshCore public channel keys: name spelling is part of the key. */
object Hashtag {
    private val pattern = Regex("(?<![\\p{L}\\p{N}_#])#[A-Za-z\\u0400-\\u052F\\u2DE0-\\u2DFF\\uA640-\\uA69F0-9_]{2,32}(?![\\p{L}\\p{N}_])")
    fun matches(text: String) = pattern.findAll(text).toList()
    fun name(input: String): String {
        val name = input.trim().let { if (it.startsWith("#")) it else "#$it" }
        if (!pattern.matches(name) || name.toByteArray().size > 32) throw NodeError(NodeError.Code.CHANNEL_NAME)
        return name
    }
    fun key(input: String): ByteArray = MessageDigest.getInstance("SHA-256").digest(name(input).toByteArray()).copyOf(16)
    fun id(name: String) = "channel:" + if (name.startsWith("#")) name else name.lowercase(java.util.Locale.ROOT)
    fun display(name: String) = if (name.startsWith("#")) name else "#$name"
}

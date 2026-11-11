package net.meshstar.rnslite

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** The LXMF MessagePack subset, with ordered maps and unsigned 64-bit preservation. */
object MsgPack {
    data class MapValue(val entries: List<Pair<Any?, Any?>> = emptyList())
    fun encode(value: Any?): ByteArray = ByteArrayOutputStream().apply { writeValue(this, value, 0) }.toByteArray()
    private fun number(out: ByteArrayOutputStream, tag: Int, value: Long, bytes: Int) {
        out.write(tag); for (i in bytes - 1 downTo 0) out.write((value ushr (8 * i)).toInt() and 255)
    }
    private fun size(out: ByteArrayOutputStream, length: Int, fix: Int?, fixLimit: Int, t8: Int?, t16: Int, t32: Int) {
        when {
            fix != null && length < fixLimit -> out.write(fix + length)
            t8 != null && length <= 255 -> number(out, t8, length.toLong(), 1)
            length <= 65535 -> number(out, t16, length.toLong(), 2)
            else -> number(out, t32, length.toLong(), 4)
        }
    }
    private fun writeValue(out: ByteArrayOutputStream, v: Any?, depth: Int) {
        checkRns(depth <= 32, "MessagePack nesting")
        when (v) {
            null -> out.write(0xc0)
            is Boolean -> out.write(if (v) 0xc3 else 0xc2)
            is Byte, is Short, is Int, is Long -> {
                val n = (v as Number).toLong()
                when {
                    n >= 0 -> unsigned(out, n.toULong())
                    n >= -32 -> out.write(n.toInt() and 255)
                    n >= -128 -> number(out, 0xd0, n, 1)
                    n >= -32768 -> number(out, 0xd1, n, 2)
                    n >= Int.MIN_VALUE -> number(out, 0xd2, n, 4)
                    else -> number(out, 0xd3, n, 8)
                }
            }
            is ULong -> unsigned(out, v)
            is Double -> number(out, 0xcb, v.toBits(), 8)
            is Float -> number(out, 0xca, v.toBits().toLong(), 4)
            is String -> {
                val b = v.toByteArray(Charsets.UTF_8); size(out, b.size, 0xa0, 32, 0xd9, 0xda, 0xdb); out.write(b)
            }
            is ByteArray -> { size(out, v.size, null, 0, 0xc4, 0xc5, 0xc6); out.write(v) }
            is List<*> -> { size(out, v.size, 0x90, 16, null, 0xdc, 0xdd); v.forEach { writeValue(out, it, depth + 1) } }
            is MapValue -> {
                size(out, v.entries.size, 0x80, 16, null, 0xde, 0xdf)
                v.entries.forEach { (k, value) -> writeValue(out, k, depth + 1); writeValue(out, value, depth + 1) }
            }
            else -> throw RNSError("Unsupported MessagePack value")
        }
    }
    private fun unsigned(out: ByteArrayOutputStream, n: ULong) {
        when {
            n < 128u -> out.write(n.toInt())
            n <= 255u -> number(out, 0xcc, n.toLong(), 1)
            n <= 65535u -> number(out, 0xcd, n.toLong(), 2)
            n <= 0xffffffffu -> number(out, 0xce, n.toLong(), 4)
            else -> number(out, 0xcf, n.toLong(), 8)
        }
    }
    fun decode(data: ByteArray): Any? = Reader(data).run {
        val value = read(0); checkRns(position == data.size, "Trailing MessagePack data"); value
    }
    private class Reader(val data: ByteArray) {
        var position = 0
        fun bytes(n: Int): ByteArray {
            checkRns(n >= 0 && n <= data.size - position, "Truncated MessagePack")
            return data.cut(position, position + n).also { position += n }
        }
        fun uint(n: Int): Long = bytes(n).fold(0L) { acc, b -> (acc shl 8) or (b.toLong() and 255) }
        fun length(n: Int): Int = uint(n).also { checkRns(it in 0..data.size.toLong(), "MessagePack length") }.toInt()
        fun text(n: Int): String = decodeText(bytes(n), Charsets.UTF_8)
        fun array(n: Int, depth: Int): List<Any?> {
            checkRns(n <= data.size - position, "MessagePack array length")
            return List(n) { read(depth + 1) }
        }
        fun map(n: Int, depth: Int): MapValue {
            checkRns(n <= (data.size - position) / 2, "MessagePack map length")
            return MapValue(List(n) { read(depth + 1) to read(depth + 1) })
        }
        fun read(depth: Int): Any? {
            checkRns(depth <= 32, "MessagePack nesting")
            val tag = uint(1).toInt()
            return when (tag) {
                in 0..127 -> tag.toLong()
                in 0xe0..0xff -> tag.toByte().toLong()
                in 0xa0..0xbf -> text(tag and 31)
                in 0x90..0x9f -> array(tag and 15, depth)
                in 0x80..0x8f -> map(tag and 15, depth)
                0xc0 -> null
                0xc2 -> false
                0xc3 -> true
                0xc4 -> bytes(length(1))
                0xc5 -> bytes(length(2))
                0xc6 -> bytes(length(4))
                0xca -> Float.fromBits(uint(4).toInt())
                0xcb -> Double.fromBits(uint(8))
                0xcc -> uint(1)
                0xcd -> uint(2)
                0xce -> uint(4)
                0xcf -> uint(8).let { if (it >= 0) it else it.toULong() }
                0xd0 -> uint(1).toByte().toLong()
                0xd1 -> uint(2).toShort().toLong()
                0xd2 -> uint(4).toInt().toLong()
                0xd3 -> uint(8)
                0xd9 -> text(length(1))
                0xda -> text(length(2))
                0xdb -> text(length(4))
                0xdc -> array(length(2), depth)
                0xdd -> array(length(4), depth)
                0xde -> map(length(2), depth)
                0xdf -> map(length(4), depth)
                else -> throw RNSError("Unsupported MessagePack tag")
            }
        }
    }
}
internal fun decodeText(bytes: ByteArray, charset: java.nio.charset.Charset): String = try {
    charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes)).toString()
} catch (_: java.nio.charset.CharacterCodingException) { throw RNSError("Invalid text encoding") }

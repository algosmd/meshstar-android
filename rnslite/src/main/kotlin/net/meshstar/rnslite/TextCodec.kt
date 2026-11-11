package net.meshstar.rnslite

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.util.zip.Inflater
import java.util.zip.DataFormatException

object TextCodec {
    private val dictionaryV1: ByteArray by lazy {
        requireNotNull(javaClass.getResourceAsStream("/textcodec-v1.bin")) { "Missing text dictionary" }.use { it.readBytes() }
    }
    fun dictionary(version: Int): ByteArray {
        checkRns(version == 1, "Unsupported dictionary version")
        return dictionaryV1.copyOf()
    }
    fun decode(encoded: ByteArray, maxOutputBytes: Int = 65_536): String {
        checkRns(encoded.isNotEmpty() && maxOutputBytes > 0, "Text frame length")
        val tag = encoded[0].toInt() and 255
        var data = encoded.cut(1)
        if (tag and 2 != 0) {
            val inflater = Inflater(true)
            try {
                inflater.setDictionary(dictionary(tag shr 2)); inflater.setInput(data)
                val out = ByteArrayOutputStream(); val buffer = ByteArray(1024)
                while (!inflater.finished()) {
                    val n = inflater.inflate(buffer)
                    checkRns(out.size() <= maxOutputBytes - n, "Text output limit")
                    if (n > 0) out.write(buffer, 0, n)
                    else checkRns(inflater.finished(), "Truncated or invalid deflate")
                }
                checkRns(inflater.remaining == 0, "Trailing deflate data")
                data = out.toByteArray()
            } catch (_: DataFormatException) { throw RNSError("Invalid deflate") }
            finally { inflater.end() }
        }
        checkRns(data.size <= maxOutputBytes, "Text output limit")
        return decodeText(data, if (tag and 1 != 0) Charset.forName("windows-1251") else Charsets.UTF_8)
    }
}

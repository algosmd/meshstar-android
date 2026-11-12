package net.meshstar.meshcore

/** Companion frames: one UART write/notification per frame, no stream delimiters. */
class NodeError(val code: Code, val detail: Int = 0) : Exception(code.name) {
    enum class Code { OFFLINE, TIMEOUT, FRAME, MTU, REJECTED, OCCUPIED, SECRET, STORAGE }
}
internal fun valid(value: Boolean) { if (!value) throw NodeError(NodeError.Code.FRAME) }
internal fun ByteArray.u(i: Int) = this[i].toInt() and 255
internal fun ByteArray.word(i: Int): Long { valid(size >= i + 4); return (0..3).fold(0L) { n, j -> n or (u(i+j).toLong() shl (8*j)) } }
internal fun ByteArray.text(a: Int, b: Int = size) = copyOfRange(a,b).takeWhile { it != 0.toByte() }.toByteArray().toString(Charsets.UTF_8)
fun little(value: Long) = ByteArray(4) { (value shr (8*it)).toByte() }
data class NodeContact(val key: ByteArray, val name: String, val type: Int, val flags: Int, val modified: Long) {
    // Location fields from firmware are deliberately not decoded, retained or displayed.
    companion object { fun parse(b: ByteArray): NodeContact {
        valid(b.size == 148 && b.u(0) in setOf(3,0x8a))
        return NodeContact(b.copyOfRange(1,33), b.text(100,132), b.u(33), b.u(34), b.word(144))
    } }
}
data class Channel(val index: Int, val name: String, val secret: ByteArray) {
    val transport get() = name.trim().equals("MESHSAT", true)
    val empty get() = name.isEmpty() && secret.all { it == 0.toByte() }
}
data class Reception(val snr: Double, val hops: Int)
data class Datagram(val channel: Int, val type: Int, val bytes: ByteArray, val reception: Reception)
data class ForeignText(val channel: Int?, val prefix: ByteArray?, val timestamp: Long, val text: String, val reception: Reception?)
object Protocol {
    const val SERVICE = "6E400001-B5A3-F393-E0A9-E50E24DCCA9E"
    const val RX = "6E400002-B5A3-F393-E0A9-E50E24DCCA9E"
    const val TX = "6E400003-B5A3-F393-E0A9-E50E24DCCA9E"
    val messages = setOf(7,8,0x10,0x11,0x1b)
    val appStart get() = byteArrayOf(1,0,0,0,0,0,0,0) + "MESHSAT".toByteArray()
    fun self(b: ByteArray): Pair<String,ByteArray> {
        valid(b.size >= 58 && b.u(0) == 5); return b.text(58) to b.copyOfRange(4,36)
    }
    fun capacity(b: ByteArray): Int {
        valid(b.size >= 8 && b.u(0)==0x0d && b.u(1)>=3 && b.u(3)>0); return minOf(40,b.u(3))
    }
    fun channel(b: ByteArray, index: Int): Channel {
        valid(b.size==50 && b.u(0)==0x12 && b.u(1)==index)
        return Channel(index,b.text(2,34),b.copyOfRange(34,50))
    }
    fun setChannel(index: Int, secret: ByteArray): ByteArray {
        if (secret.size!=16 || secret.all { it==0.toByte() }) throw NodeError(NodeError.Code.SECRET)
        valid(index in 0..39)
        return byteArrayOf(0x20,index.toByte()) + "MESHSAT".toByteArray().copyOf(32) + secret
    }
    /** Preserve every occupied slot unless the caller has explicit, one-attempt replacement consent. */
    fun choose(channels: List<Channel>, desired: Int, secret: ByteArray?, replace: Boolean): Pair<Channel,Boolean> {
        valid(channels.isNotEmpty() && desired in 0..39)
        if (secret!=null && (secret.size!=16 || secret.all { it==0.toByte() })) throw NodeError(NodeError.Code.SECRET)
        if (replace && secret==null) throw NodeError(NodeError.Code.SECRET)
        if (desired >= channels.size && secret!=null) throw NodeError(NodeError.Code.FRAME)
        var chosen=channels[minOf(desired,channels.lastIndex)]
        if (!replace && chosen.name!="MESHSAT" && (secret==null || chosen.name.isNotEmpty())) {
            channels.firstOrNull { it.name=="MESHSAT" && it.secret.any { n->n!=0.toByte() } && (secret==null || it.secret.contentEquals(secret)) }?.let { chosen=it }
        }
        if (chosen.name=="MESHSAT" && chosen.secret.any { it!=0.toByte() } && (secret==null || chosen.secret.contentEquals(secret))) return chosen to false
        if (secret==null) return chosen to false // companion-only, not RNS-ready
        if (!replace && !chosen.empty) throw NodeError(NodeError.Code.OCCUPIED)
        return Channel(chosen.index,"MESHSAT",secret) to true
    }
    fun sendData(payload: ByteArray,index: Int): ByteArray {
        valid(index in 0..39 && payload.size in 1..163)
        return byteArrayOf(0x3e,index.toByte(),-1,-1,-1) + payload
    }
    fun datagram(b: ByteArray): Datagram {
        valid(b.size>=9 && b.u(0)==0x1b && b.u(8)<=163 && b.size==9+b.u(8))
        return Datagram(b.u(4),b.u(6) or (b.u(7) shl 8),b.copyOfRange(9,b.size),Reception(b[1].toDouble()/4,if(b.u(5)==255)0 else b.u(5) and 63))
    }
    fun foreign(b: ByteArray): ForeignText {
        valid(b.isNotEmpty() && b.u(0) in setOf(7,8,0x10,0x11))
        val o=if(b.u(0) in setOf(0x10,0x11))4 else 1
        val direct=b.u(0) in setOf(7,0x10)
        valid(b.size>=o+if(direct)12 else 7)
        val hop=b.u(o+if(direct)6 else 1)
        val reception=if(o==4)Reception(b[1].toDouble()/4,if(hop==255)0 else hop and 63) else null
        val start=o+if(direct)12+(if(b.u(o+7)==2)4 else 0) else 7
        valid(start<=b.size)
        return ForeignText(if(direct)null else b.u(o),if(direct)b.copyOfRange(o,o+6)else null,b.word(o+if(direct)8 else 3),b.text(start),reception)
    }
    fun channelText(text: String,index: Int,timestamp: Long,nodeName: String): ByteArray {
        val b=text.toByteArray(Charsets.UTF_8);valid(b.isNotEmpty() && b.size<=minOf(133,160-nodeName.toByteArray().size-2) && 0.toByte() !in b)
        return byteArrayOf(3,0,index.toByte())+little(timestamp)+b
    }
    fun directText(text: String,key: ByteArray,timestamp: Long): ByteArray {
        val b=text.toByteArray(Charsets.UTF_8);valid(key.size==32 && b.size in 1..133 && 0.toByte() !in b)
        return byteArrayOf(2,0,0)+little(timestamp)+key.copyOf(6)+b
    }
}

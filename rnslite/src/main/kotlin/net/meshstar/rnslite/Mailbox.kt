package net.meshstar.rnslite

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.nio.ByteBuffer
import java.util.concurrent.CancellationException

object Mailbox {
    private val salt = "meshsat-mailbox-v1".toByteArray(Charsets.US_ASCII)
    fun address(me: Identity, peer: PublicIdentity, recipient: ByteArray, day: LocalDate): ByteArray {
        checkRns(recipient.size == 16 && (Hash.equal(recipient, me.lxmfAddress) || Hash.equal(recipient, peer.lxmfAddress)), "Mailbox recipient")
        val date = day.toString()
        checkRns(date.length == 10, "Mailbox UTC day")
        return Hash.hkdf(me.sharedKey(peer), salt, recipient + date.toByteArray(Charsets.US_ASCII), 16)
    }
    fun pickupAddresses(me: Identity, peer: PublicIdentity, now: Instant): List<ByteArray> {
        val day = now.atZone(ZoneOffset.UTC).toLocalDate()
        return (-1L..1L).map { address(me, peer, me.lxmfAddress, day.plusDays(it)) }
    }
    fun publicAddress(destination: ByteArray): ByteArray {
        checkRns(destination.size == 16, "Mailbox destination")
        return Hash.truncated("meshsat-public".toByteArray(Charsets.US_ASCII) + destination)
    }
}
object MailboxStamp {
    private val domain = "meshsat-stamp-v1".toByteArray(Charsets.US_ASCII)
    fun digest(mailbox: ByteArray, data: ByteArray, nonce: ByteArray): ByteArray {
        checkRns(mailbox.size == 16 && nonce.size == 8, "Stamp parameters")
        return Hash.sha256(domain + mailbox + Hash.sha256(data) + nonce)
    }
    fun leadingZeroBits(digest: ByteArray): Int {
        var count = 0
        for (b in digest) {
            val n = b.toInt() and 255
            if (n == 0) count += 8 else return count + Integer.numberOfLeadingZeros(n) - 24
        }
        return count
    }
    fun verify(mailbox: ByteArray, data: ByteArray, nonce: ByteArray, bits: Int): Boolean =
        bits in 0..256 && leadingZeroBits(digest(mailbox, data, nonce)) >= bits
    /** Mobile work ceiling and cancellation keep a remote difficulty from causing unbounded CPU use. */
    fun solve(mailbox: ByteArray, data: ByteArray, bits: Int, cancelled: () -> Boolean = { Thread.currentThread().isInterrupted }): ByteArray {
        checkRns(mailbox.size == 16 && bits in 0..24, "Unsupported stamp difficulty")
        val prefix = domain + mailbox + Hash.sha256(data)
        var counter = 0L
        while (true) {
            if (counter and 1023 == 0L && cancelled()) throw CancellationException("Stamp cancelled")
            val nonce = ByteBuffer.allocate(8).putLong(counter).array()
            if (leadingZeroBits(Hash.sha256(prefix + nonce)) >= bits) return nonce
            checkRns(counter != Long.MAX_VALUE, "Stamp exhausted")
            counter++
        }
    }
}

package net.meshstar.rnslite

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer

class RNSError(message: String) : IllegalArgumentException(message)
internal fun checkRns(ok: Boolean, message: String) { if (!ok) throw RNSError(message) }
fun ByteArray.hex(): String = joinToString("") { "%02x".format(it.toInt() and 255) }
fun String.unhex(): ByteArray {
    checkRns(length % 2 == 0 && all { it.digitToIntOrNull(16) != null }, "Invalid hex")
    return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
internal fun ByteArray.cut(start: Int, end: Int = size): ByteArray {
    checkRns(start >= 0 && end >= start && end <= size, "Truncated data")
    return copyOfRange(start, end)
}
object Hash {
    fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)
    fun truncated(bytes: ByteArray): ByteArray = sha256(bytes).cut(0, 16)
    fun equal(a: ByteArray, b: ByteArray): Boolean = MessageDigest.isEqual(a, b)
    fun hmac(key: ByteArray, data: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(key, "HmacSHA256")); doFinal(data)
    }
    fun hkdf(input: ByteArray, salt: ByteArray = byteArrayOf(), info: ByteArray = byteArrayOf(), length: Int = 64): ByteArray {
        checkRns(length in 1..8160, "HKDF length")
        val prk = hmac(if (salt.isEmpty()) ByteArray(32) else salt, input)
        var previous = byteArrayOf()
        val out = java.io.ByteArrayOutputStream()
        for (i in 1..((length + 31) / 32)) {
            previous = hmac(prk, previous + info + byteArrayOf(i.toByte())); out.write(previous)
        }
        return out.toByteArray().cut(0, length)
    }
}
internal fun randomBytes(size: Int) = ByteArray(size).also { SecureRandom().nextBytes(it) }

/** Reticulum's authenticated AES-CBC token. Verify HMAC before attempting decryption. */
class Token(key: ByteArray) {
    private val key = key.copyOf().also { checkRns(it.size == 64, "Token key length") }
    fun encrypt(plain: ByteArray, iv: ByteArray = randomBytes(16)): ByteArray {
        checkRns(iv.size == 16, "IV length")
        val body = iv + cipher(Cipher.ENCRYPT_MODE, iv).doFinal(plain)
        return body + Hash.hmac(key.cut(0, 32), body)
    }
    fun decrypt(token: ByteArray): ByteArray {
        checkRns(token.size >= 64 && (token.size - 48) % 16 == 0, "Token length")
        val body = token.cut(0, token.size - 32)
        checkRns(Hash.equal(Hash.hmac(key.cut(0, 32), body), token.cut(token.size - 32)), "Invalid authentication tag")
        return try { cipher(Cipher.DECRYPT_MODE, body.cut(0, 16)).doFinal(body.cut(16)) }
        catch (_: java.security.GeneralSecurityException) { throw RNSError("Invalid encrypted payload") }
    }
    private fun cipher(mode: Int, iv: ByteArray): Cipher = Cipher.getInstance("AES/CBC/PKCS5Padding").apply {
        init(mode, SecretKeySpec(key.cut(32), "AES"), IvParameterSpec(iv))
    }
}

open class PublicIdentity(publicKey: ByteArray) {
    private val key = publicKey.copyOf().also { checkRns(it.size == 64, "Public key length") }
    val publicKey: ByteArray get() = key.copyOf()
    val hash: ByteArray get() = Hash.truncated(key)
    val lxmfAddress: ByteArray get() = Hash.truncated(Hash.sha256("lxmf.delivery".toByteArray()).cut(0, 10) + hash)
    val fingerprint: String get() = hash.hex().take(16).chunked(4).joinToString(" ")
    fun validate(signature: ByteArray, message: ByteArray): Boolean {
        if (signature.size != 64) return false
        return try { Ed25519Signer().run {
            init(false, Ed25519PublicKeyParameters(key, 32)); update(message, 0, message.size); verifySignature(signature)
        } } catch (_: IllegalArgumentException) { false }
    }
    fun encrypt(plain: ByteArray, ephemeralPrivateKey: ByteArray = randomBytes(32), iv: ByteArray = randomBytes(16)): ByteArray {
        checkRns(ephemeralPrivateKey.size == 32, "Ephemeral key length")
        val eph = X25519PrivateKeyParameters(ephemeralPrivateKey, 0)
        val shared = exchange(eph, key.cut(0, 32))
        return eph.generatePublicKey().encoded + Token(Hash.hkdf(shared, hash)).encrypt(plain, iv)
    }
    companion object {
        internal fun exchange(privateKey: X25519PrivateKeyParameters, publicKey: ByteArray): ByteArray {
            checkRns(publicKey.size == 32, "X25519 public key length")
            return try { ByteArray(32).also { privateKey.generateSecret(X25519PublicKeyParameters(publicKey, 0), it, 0) } }
            catch (_: IllegalStateException) { throw RNSError("Invalid X25519 peer") }
        }
    }
}
class Identity(privateKey: ByteArray = randomBytes(64)) : PublicIdentity(derivePublic(privateKey)) {
    private val key = privateKey.copyOf()
    fun exportPrivateKey(): ByteArray = key.copyOf()
    fun sharedKey(peer: PublicIdentity): ByteArray = exchange(X25519PrivateKeyParameters(key, 0), peer.publicKey.cut(0, 32))
    fun sign(message: ByteArray): ByteArray = Ed25519Signer().run {
        init(true, Ed25519PrivateKeyParameters(key, 32)); update(message, 0, message.size); generateSignature()
    }
    fun decrypt(ciphertext: ByteArray): ByteArray {
        checkRns(ciphertext.size >= 96, "Encrypted payload length")
        val shared = exchange(X25519PrivateKeyParameters(key, 0), ciphertext.cut(0, 32))
        return Token(Hash.hkdf(shared, hash)).decrypt(ciphertext.cut(32))
    }
    companion object {
        private fun derivePublic(key: ByteArray): ByteArray {
            checkRns(key.size == 64, "Private key length")
            return X25519PrivateKeyParameters(key, 0).generatePublicKey().encoded +
                Ed25519PrivateKeyParameters(key, 32).generatePublicKey().encoded
        }
    }
}

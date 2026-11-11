package net.meshstar.identity

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import net.meshstar.rnslite.Identity

/** Android 26 cannot hold the RNS X25519/Ed25519 pair natively. A non-exportable
 * Keystore AES key seals its 64-byte seed and all persisted application data. */
class EncryptedFiles(context: Context, private val namespace: String = "meshstar") {
    init { require(namespace.matches(Regex("[a-z0-9.-]+"))) }
    private val directory = File(context.noBackupFilesDir, namespace).apply { mkdirs() }
    private val alias = "net.$namespace.rns.storage.v1"
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        check(directory.listFiles().orEmpty().none { it.isFile }) { "Keystore key missing; stored identity was not replaced" }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).setUserAuthenticationRequired(false).build())
            generateKey()
        }
    }
    @Synchronized fun read(name: String): ByteArray? {
        val file = AtomicFile(File(directory, name))
        if (!file.baseFile.exists() && !File(directory, "$name.bak").exists()) return null
        val bytes = file.readFully()
        check(bytes.size in 29..32_000_000 && bytes[0] == 1.toByte()) { "Invalid encrypted storage" }
        return Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(1, 13)))
            updateAAD(name.toByteArray(Charsets.UTF_8)); doFinal(bytes.copyOfRange(13, bytes.size))
        }
    }
    @Synchronized fun write(name: String, plain: ByteArray) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, key()); updateAAD(name.toByteArray(Charsets.UTF_8))
        }
        val data = byteArrayOf(1) + cipher.iv + cipher.doFinal(plain)
        val file = AtomicFile(File(directory, name)); val stream = file.startWrite()
        try { stream.write(data); file.finishWrite(stream) }
        catch (error: Exception) { file.failWrite(stream); throw error }
    }
    @Synchronized fun erase() {
        if (directory.listFiles().orEmpty().isEmpty()) { directory.delete(); return }
        KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(alias) }
        check(directory.deleteRecursively()) { "Encrypted storage cleanup failed" }
    }
    @Synchronized fun identity(): Identity {
        read("identity")?.let { return Identity(it) }
        check(read("state") == null) { "Identity missing; history was not replaced" }
        val identity = Identity()
        val seed = identity.exportPrivateKey()
        try { write("identity", seed) } finally { seed.fill(0) }
        return identity
    }
}

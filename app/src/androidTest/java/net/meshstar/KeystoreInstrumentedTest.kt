package net.meshstar

import androidx.test.platform.app.InstrumentationRegistry
import net.meshstar.identity.EncryptedFiles
import net.meshstar.link.Contact
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.KeyStore
import java.util.UUID

/** Runs against real AndroidKeyStore on API 26/34, not a software/mock provider. */
class KeystoreInstrumentedTest {
    @Test fun identityAndEncryptedStateSurviveReopenAndRejectTampering() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val namespace = "meshstar.test." + UUID.randomUUID().toString()
        val directory = File(context.noBackupFilesDir, namespace)
        val alias = "net.$namespace.rns.storage.v1"
        try {
            val files = EncryptedFiles(context, namespace)
            val me = files.identity()
            val qr = Contact.of(me, "Synthetic").qr
            files.write("state", "synthetic state".toByteArray())
            val reopened = EncryptedFiles(context, namespace)
            assertEquals(qr, Contact.of(reopened.identity(), "Synthetic").qr)
            assertArrayEquals("synthetic state".toByteArray(), reopened.read("state"))
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            assertNull(store.getKey(alias, null).encoded)
            val stored = File(directory, "identity").readBytes()
            assertFalse(stored.contentEquals(me.exportPrivateKey()))
            stored[stored.lastIndex] = (stored.last().toInt() xor 1).toByte()
            File(directory, "identity").writeBytes(stored)
            assertTrue(runCatching { reopened.identity() }.isFailure)
            assertArrayEquals(stored, File(directory, "identity").readBytes()) // Never silently rotate.
            store.deleteEntry(alias)
            assertTrue(runCatching { reopened.identity() }.isFailure)
        } finally {
            directory.deleteRecursively()
            KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(alias) }
        }
    }
}

package net.meshstar

import net.meshstar.meshcore.Hashtag
import org.junit.Assert.*
import org.junit.Test

class HashtagAndroidTest {
    @Test fun androidRegexAndLegacyContactCodes(){
        assertEquals(listOf("#demo","#тест"),Hashtag.matches("join #demo #тест").map{it.value})
        assertEquals("#demo",Hashtag.name("demo"))
        val contact=net.meshstar.link.Contact.of(net.meshstar.rnslite.Identity(ByteArray(64){11}),"Synthetic")
        assertTrue(contact.qr.startsWith("meshstar1:"))
        assertEquals(contact,net.meshstar.link.Contact.parse(contact.qr.replace("meshstar1:","meshsat1:")))
    }
}

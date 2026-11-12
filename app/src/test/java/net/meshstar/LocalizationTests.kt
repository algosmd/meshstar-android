package net.meshstar

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class LocalizationTests {
    @Test fun allMeshStarLabelsHaveFiveTranslationsAndNoUpstreamBrand() {
        fun strings(locale: String): Map<String,String> {
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/res/values$locale/meshstar_strings.xml"))
            val nodes = doc.getElementsByTagName("string")
            return (0 until nodes.length).associate { nodes.item(it).attributes.getNamedItem("name").nodeValue to nodes.item(it).textContent }
        }
        val source = strings("")
        for (locale in listOf("-ru", "-ro", "-de", "-fr")) {
            val translated = strings(locale)
            assertEquals(source.keys, translated.keys)
            assertTrue(translated.values.none { it.isBlank() || it.contains("bitchat", true) })
        }
        assertTrue(source.values.none { it.contains("bitchat", true) })
        for (locale in listOf("", "-ru", "-ro", "-de", "-fr")) {
            assertTrue(strings(locale).getValue("ms_signature_ok").endsWith(" ✓"))
        }
    }
}

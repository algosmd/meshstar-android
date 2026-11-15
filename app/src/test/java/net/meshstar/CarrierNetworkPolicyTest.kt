package net.meshstar

import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import net.meshstar.box.*
import net.meshstar.data.*
import net.meshstar.rnslite.Identity
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34], application = android.app.Application::class, manifest = org.robolectric.annotation.Config.NONE)
class CarrierNetworkPolicyTest {
    @Test fun allowsOnlySpecifiedLiteralRangesAndLoopback() {
        // Synthetic range boundaries, never contacted.
        listOf("10.0.0.0", "10.255.255.255", "172.16.0.0", "172.31.255.255",
            "192.168.0.0", "192.168.255.255", "100.64.0.0", "100.127.255.255",
            "127.0.0.1", "localhost", "::1").forEach {
            assertTrue(it, CarrierNetworkPolicy.allowsCleartextHost(it))
        }
        listOf("9.255.255.255", "11.0.0.0", "172.15.255.255", "172.32.0.0",
            "192.167.255.255", "192.169.0.0", "100.63.255.255", "100.128.0.0",
            "192.0.2.1", "169.254.0.1", "0.0.0.0", "::", "::ffff:127.0.0.1",
            "localhost.example", "node.local", "10.0.0.1.example", "10.0.0.256",
            "10.0.0", "010.0.0.1", "2130706433", "127.1").forEach {
            assertFalse(it, CarrierNetworkPolicy.allowsCleartextHost(it))
        }
    }

    @Test fun publicHttpsAllowedButPublicHttpAndHostSpoofingRejected() {
        assertEquals("https", CarrierHttp.endpoint("https://server.example").scheme)
        listOf("http://server.example", "http://192.0.2.1", "http://localhost.example",
            "http://10.0.0.1.example", "http://node.local", "http://localhost.").forEach {
            assertTrue(it, runCatching { CarrierHttp.endpoint(it) }.exceptionOrNull() is CleartextEndpointRejected)
        }
        assertTrue(runCatching { CarrierHttp.endpoint("http://localhost@server.example") }.isFailure)
    }

    @Test fun rejectedSettingsPreserveExistingConfiguration() = runBlocking {
        val original = SatState(internet = "https://server.example", box = BoxConfig("https://box.example", "synthetic-credential"))
        val engine = SatEngine(Identity(), MemoryStore(original))
        assertTrue(runCatching { engine.configureInternet("http://server.example") }.exceptionOrNull() is CleartextEndpointRejected)
        assertTrue(runCatching { engine.configureBox("http://box.example", "synthetic-credential") }.exceptionOrNull() is CleartextEndpointRejected)
        assertEquals(original.internet, engine.state.value.internet)
        assertEquals(original.box, engine.state.value.box)
        engine.configureInternet("")
        assertEquals("", engine.state.value.internet)
    }

    @Test fun publicHttpFailsBeforeDnsOrCredentialTransmission() = runBlocking {
        var resolutions = 0
        val client = OkHttpClient.Builder().dns { resolutions++; error("Must not resolve") }.build()
        val error = runCatching { CarrierHttp(client).request("http://server.example", "api/status", "synthetic-credential") }.exceptionOrNull()
        assertTrue(error is CleartextEndpointRejected)
        assertEquals(0, resolutions)
        assertEquals("HTTPS required for public servers", safeFailure(error!!))
    }

    @Test fun localHttpWorksWithoutDnsAndRedirectsNeverForwardCredentials() = runBlocking {
        MockWebServer().use { server ->
            server.start(InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)), 0)
            val base = "http://localhost:${server.port}"
            val client = OkHttpClient.Builder().followRedirects(true).dns { error("localhost must be pinned") }.build()
            val http = CarrierHttp(client)
            server.enqueue(MockResponse.Builder().body("ok").build())
            assertEquals("ok", http.request(base, "api/status", "synthetic-credential"))
            assertEquals("synthetic-credential", server.takeRequest().headers["X-Box-Token"])
            server.enqueue(MockResponse.Builder().code(302).addHeader("Location", "http://server.example/leak").build())
            val error = runCatching { http.request(base, "api/status", "synthetic-credential") }.exceptionOrNull()
            assertTrue(error is HttpFailure && error.code == 302)
            assertEquals(2, server.requestCount)
        }
    }
}

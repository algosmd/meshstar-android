package net.meshstar

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockWebServer
import net.meshstar.compliance.RegionPolicy
import net.meshstar.meshcore.*
import net.meshstar.data.*
import net.meshstar.box.BoxConfig
import net.meshstar.link.Contact
import net.meshstar.rnslite.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class,manifest=Config.NONE)
class RegionalComplianceTest {
    private fun policy():RegionPolicy {
        val app=ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("meshstar.region",0).edit().clear().commit()
        return RegionPolicy(app)
    }
    @Test fun catalogPreservesAllCommunityProfilesIncludingRussia() {
        assertEquals(49,RadioPreset.all.size)
        assertEquals(27,RegionalRules.presets("RU").size)
        assertEquals(8,RadioPreset.all.first{it.id=="eu_narrow"}.radio.cr)
        assertEquals(5,RadioPreset.all.first{it.name=="EU/UK (Narrow)"}.radio.cr)
        assertTrue(RegionalRules.presets("US").all{!it.ham&&it.radio.frequency>900})
        assertTrue(RegionalRules.presets("IN").isEmpty())
        assertTrue(RegionalRules.presets("MD").any{it.radio.frequency<500})
    }
    @Test fun confirmationPersistsButSimChangeRevokesNodeApproval() {
        val p=policy();p.observeSim("fixture-a","US","GB")
        assertEquals("",p.state.value.country);assertEquals("US",p.state.value.hint)
        p.chooseCountry("MD");p.confirm("eu_narrow")
        assertFalse(p.mayAdvertise("fixture"));p.approve("fixture");assertTrue(p.mayAdvertise("fixture"))
        val restored=RegionPolicy(ApplicationProvider.getApplicationContext<Application>())
        assertTrue(restored.mayAdvertise("fixture"))
        restored.observeSim("fixture-b","IN","GB")
        assertEquals("MD",restored.state.value.country);assertFalse(restored.mayAdvertise("fixture"));assertNull(restored.state.value.selected)
    }
    @Test fun customProfileMustFitWholeChannelAndKnownCountryBand() {
        val p=policy();p.chooseCountry("IN")
        assertTrue(runCatching{p.confirm("eu_narrow")}.isFailure)
        p.confirm("custom",RadioParameters(866.0,125.0,7,5))
        assertNotNull(p.state.value.selected)
        assertTrue(runCatching{p.confirm("custom",RadioParameters(865.0,125.0,7,5))}.isFailure)
        assertFalse(RegionalRules.permits(RadioPreset.all.first().radio,"UA"))
        assertFalse(RegionalRules.permits(RadioPreset.all.first().radio,"ZZ"))
    }
    @Test fun satelliteBlockAppliesToQueuedPostsButLeavesRelayAndInternetUsable()=runBlocking {
        val p=policy()
        for(country in listOf("IN","CN","RU","IR","PK","BD","EG","MM","SD","AF")){p.chooseCountry(country);assertFalse(p.state.value.satelliteAllowed)}
        p.chooseCountry("MD");assertTrue(p.state.value.satelliteAllowed)
        val me=Identity(ByteArray(64){(it+1).toByte()});val peer=Identity(ByteArray(64){(it+80).toByte()})
        MockWebServer().use{server->server.start()
            val state=SatState(box=BoxConfig(server.url("/").toString().trimEnd('/'),"synthetic","08".repeat(16),"06".repeat(16)),internet=server.url("/").toString().trimEnd('/'),contacts=mutableListOf(Contact.of(peer,"fixture")),node=NodeConfiguration("fixture","fixture",7,"07".repeat(16),"09".repeat(32)))
            val engine=SatEngine(me,MemoryStore(state));engine.satelliteAllowed={p.state.value.satelliteAllowed}
            engine.send(peer.lxmfAddress.hex(),"queued",Route.SATELLITE)
            p.chooseCountry("IN")
            engine.flushOutgoing(Route.SATELLITE)
            assertEquals(0,server.requestCount)
            assertTrue(runCatching{engine.send(peer.lxmfAddress.hex(),"blocked",Route.SATELLITE)}.isFailure)
            engine.send(peer.lxmfAddress.hex(),"relay",Route.RELAY)
            engine.send(peer.lxmfAddress.hex(),"internet",Route.INTERNET)
            assertEquals(3,engine.state.value.messages.size)
        }
    }
}

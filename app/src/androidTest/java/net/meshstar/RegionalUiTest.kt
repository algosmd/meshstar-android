package net.meshstar

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import net.meshstar.compliance.RegionPolicy
import net.meshstar.ui.*
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import java.io.File

class RegionalUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun communityWarningRequiresExplicitConfirmationInFiveLocales(){
        var language by mutableStateOf("en")
        var region by mutableStateOf(RegionPolicy.State(country="MD",hint="MD"))
        val actions=mutableListOf<UiAction>()
        compose.setContent {
            val localized=localizedContext(LocalContext.current,language)
            CompositionLocalProvider(LocalContext provides localized){key(language){MeshTheme(false){
                Column(Modifier.testTag("warning").verticalScroll(rememberScrollState())){
                    RegionControls(region){actions.add(it);if(it is UiAction.RegionCountry)region=region.copy(country=it.code);true}
                }
            }}}
        }
        val base=InstrumentationRegistry.getInstrumentation().targetContext
        val dir=File(base.getExternalFilesDir(null),"compliance-warning").apply{mkdirs()}
        for(code in listOf("en","ru","ro","de","fr")){
            compose.runOnIdle{language=code;actions.clear()};compose.waitForIdle()
            val context=localizedContext(base,code)
            val country=java.util.Locale("","MD").getDisplayCountry(java.util.Locale.forLanguageTag(code))
            compose.onNodeWithText(context.getString(T.compliance_suggested.resource)+": "+country).performClick()
            val preset=net.meshstar.meshcore.RegionalRules.presets("MD").first{it.name.contains("Off-Grid")}
            val apply=context.getString(T.compliance_confirm.resource)+" · "+preset.name
            compose.onNodeWithText(apply).performScrollTo().performClick()
            val warning=context.getString(T.compliance_community_warning.resource,country)
            compose.onNodeWithText(warning).performScrollTo().assertIsDisplayed()
            assertTrue(actions.filterIsInstance<UiAction.RegionConfirm>().isEmpty())
            compose.onNodeWithText(context.getString(T.back.resource)).performScrollTo().performClick()
            compose.onNodeWithText(warning).assertDoesNotExist()
            assertTrue(actions.filterIsInstance<UiAction.RegionConfirm>().isEmpty())
            compose.onNodeWithText(apply).performScrollTo().performClick()
            compose.onNodeWithText(context.getString(T.compliance_accept_warning.resource)).performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("warning").captureToImage().asAndroidBitmap().let{bitmap->File(dir,"$code.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}}
            compose.onNodeWithText(context.getString(T.compliance_accept_warning.resource)).performClick()
            compose.waitForIdle()
            val confirmed=actions.filterIsInstance<UiAction.RegionConfirm>().single()
            assertEquals(preset.id,confirmed.id);assertTrue(confirmed.acknowledgeWarning);assertNull(confirmed.custom)
        }
    }
    @Test fun wizardInFiveLocalesAndBothThemes(){
        data class Scene(val locale:String="en",val dark:Boolean=false)
        var scene by mutableStateOf(Scene())
        val model=unifiedFixture().copy(region=RegionPolicy.State(hint="MD"))
        compose.setContent{
            val base=LocalContext.current
            val localized=remember(scene.locale){localizedContext(base,scene.locale)}
            CompositionLocalProvider(LocalContext provides localized){key(scene){MeshTheme(scene.dark){UnifiedScreen(model,{true},initialPage="region")}}}
        }
        val dir=File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),"compliance").apply{mkdirs()}
        for(locale in listOf("en","ru","ro","de","fr"))for(dark in listOf(false,true)){
            compose.runOnIdle{scene=Scene(locale,dark)};compose.waitForIdle()
            compose.onNodeWithTag("meshstar.unified").assertIsDisplayed().captureToImage().asAndroidBitmap().let{bitmap->File(dir,"$locale-${if(dark)"dark"else"light"}.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}}
        }
    }
    @Test fun restrictedCountryRemovesSatelliteButPreservesRelayAndInternet(){
        val model=unifiedFixture().copy(region=RegionPolicy.State(country="IN"))
        compose.setContent{MeshTheme(false){UnifiedScreen(model,{true},requestedPeer="alice",initialPage="chat",initialRoutes=true)}}
        compose.onNodeWithText("Satellite").assertDoesNotExist()
        compose.onNodeWithText("Relay").assertExists()
        compose.onNodeWithText("Internet").assertExists()
        compose.onNodeWithText("In this country, a satellite terminal requires a licence.").assertExists()
    }
}

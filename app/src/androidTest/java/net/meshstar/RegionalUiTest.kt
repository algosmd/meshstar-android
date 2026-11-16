package net.meshstar

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import net.meshstar.compliance.RegionPolicy
import net.meshstar.ui.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class RegionalUiTest {
    @get:Rule val compose=createComposeRule()
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

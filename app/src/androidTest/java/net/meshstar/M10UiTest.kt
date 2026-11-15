package net.meshstar

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import net.meshstar.meshcore.*
import net.meshstar.ui.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class M10UiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun syntheticScreensInFiveLocalesAndThemes(){
        data class Scene(val locale:String="en",val dark:Boolean=false,val page:String="channels")
        var scene by mutableStateOf(Scene())
        val node=NodeState("Synthetic node",1,true,true,listOf(Channel(0,"Public",byteArrayOf()),Channel(1,"MESHSAT",byteArrayOf()),Channel(2,"#demo",byteArrayOf()),Channel(3,"",byteArrayOf())),settings=NodeSettings("Synthetic node",ByteArray(32){7},RadioPreset.all[0].radio,22,22,0.0,0.0,1,0,0,0,"fixture",false,3600))
        val repeater=NodeContact(ByteArray(32){9},"Synthetic repeater",2,0,0,1,byteArrayOf(3).copyOf(64))
        val model=unifiedFixture().copy(node=node.copy(contacts=listOf(repeater)),pathState=PathState(repeater),admin=AdminState(repeater,true,lines=listOf("> time","UTC fixture"),status=RepeaterStatus(3600,-110,-70,12,8,5,120,4)))
        compose.setContent{
            val base=LocalContext.current
            val context=remember(scene.locale){localizedContext(base,scene.locale)}
            CompositionLocalProvider(LocalContext provides context){key(scene){MeshTheme(scene.dark){UnifiedScreen(model,{true},initialPage=scene.page,initialTab=ChatTab.LORA,initialNodeKey="09".repeat(32))}}}
        }
        val args=InstrumentationRegistry.getArguments()
        val folder=File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),"m10/"+(args.getString("viewport")?:"screen")).apply{mkdirs()}
        for(locale in listOf("en","ru","ro","de","fr"))for(dark in listOf(false,true))for(page in listOf("channels","radio","admin","nodecard")){
            compose.runOnIdle{scene=Scene(locale,dark,page)};compose.waitForIdle()
            compose.onNodeWithTag("meshstar.unified").assertIsDisplayed().captureToImage().asAndroidBitmap().let { bitmap->File(folder,"$locale-${if(dark)"dark"else"light"}-$page.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)} }
        }
    }
}

package net.meshstar

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import net.meshstar.meshcore.*
import net.meshstar.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class MeshCoreUiInstrumentedTest {
    @get:Rule val compose=createComposeRule()
    @Test fun occupiedChannelRequiresSeparateExplicitConfirmationAndSecret(){
        val actions=mutableListOf<UiAction>()
        val model=unifiedFixture().copy(nodeConfig=NodeConfiguration("synthetic","Synthetic",7),nodeConflict=true,nodeStatus="node_occupied")
        compose.setContent{MeshTheme(false){UnifiedScreen(model,{actions.add(it);true},initialPage="node")}}
        compose.onNodeWithText("replace channel").performScrollTo().performClick()
        compose.onNodeWithText("confirm replacement").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("meshstar.node.secret").performScrollTo().performTextInput("07".repeat(16))
        compose.onNodeWithText("confirm replacement").performScrollTo().performClick()
        compose.waitForIdle()
        val request=actions.filterIsInstance<UiAction.ConfigureNode>().single()
        assertTrue(request.replace);assertEquals(7,request.slot);assertEquals("synthetic",request.id)
    }
    @Test fun radioConfirmationStaysAtApplyAndSendsSelectedCrOnlyAfterConfirm(){
        val actions=mutableListOf<UiAction>()
        val settings=NodeSettings("Synthetic",ByteArray(32),RadioPreset.all[0].radio.copy(cr=5),22,22,0.0,0.0,1,0,0,0)
        val model=unifiedFixture().copy(node=NodeState("Synthetic",7,true,true,settings=settings))
        compose.setContent{MeshTheme(false){UnifiedScreen(model,{actions.add(it);true},initialPage="radio")}}
        repeat(3){compose.onNodeWithText("CR +").performScrollTo().performClick()}
        compose.onNodeWithText("CR 4/8").assertExists()
        compose.onNodeWithText("apply radio settings").performScrollTo().performClick()
        // No scrolling to a second confirmation at the bottom of the settings page.
        compose.onNodeWithText("confirm").assertIsDisplayed()
        assertTrue(actions.filterIsInstance<UiAction.ChangeNode>().isEmpty())
        compose.onNodeWithText("confirm").performClick()
        compose.waitForIdle()
        val change=actions.filterIsInstance<UiAction.ChangeNode>().single().change as NodeChange.Radio
        assertEquals(settings.radio.copy(cr=8),change.value)
        compose.onNodeWithText("saved and verified").assertExists()
    }
    @Test fun transportChannelNeverBecomesTextChat(){
        val model=unifiedFixture().copy(node=NodeState("Synthetic",7,true,true,listOf(Channel(0,"Public",byteArrayOf()),Channel(7,"MESHSAT",byteArrayOf()),Channel(2,"",byteArrayOf()))))
        compose.setContent{MeshTheme(false){UnifiedScreen(model,{true},initialPage="node")}}
        compose.onNodeWithText("MeshStar channel · configured").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("#Public").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("#MESHSAT").assertDoesNotExist()
        compose.onNodeWithText("#").assertDoesNotExist()
    }
}

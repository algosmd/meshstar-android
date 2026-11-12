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
    @Test fun transportChannelNeverBecomesTextChat(){
        val model=unifiedFixture().copy(node=NodeState("Synthetic",7,true,true,listOf(Channel(0,"Public",byteArrayOf()),Channel(7,"MESHSAT",byteArrayOf()),Channel(2,"",byteArrayOf()))))
        compose.setContent{MeshTheme(false){UnifiedScreen(model,{true},initialPage="node")}}
        compose.onNodeWithText("MeshStar channel · configured").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("#Public").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("#MESHSAT").assertDoesNotExist()
        compose.onNodeWithText("#").assertDoesNotExist()
    }
}

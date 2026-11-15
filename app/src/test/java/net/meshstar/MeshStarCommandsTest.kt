package net.meshstar

import net.meshstar.ui.MeshStarCommands
import org.junit.Assert.*
import org.junit.Test

class MeshStarCommandsTest {
    @Test fun financialCommandsCannotReachTheUpstreamSender() {
        for (command in listOf("/pay", "/pay synthetic", "/PAY synthetic", " /pay synthetic"))
            assertFalse(MeshStarCommands.allowed(command))
        for (message in listOf("hello", "/msg Synthetic hello", "/block Synthetic", "what is /pay?"))
            assertTrue(MeshStarCommands.allowed(message))
    }
}

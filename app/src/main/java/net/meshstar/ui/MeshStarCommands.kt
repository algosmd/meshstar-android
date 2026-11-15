package net.meshstar.ui

/** Legacy financial commands are not part of the MeshStar product. */
object MeshStarCommands {
    fun allowed(content: String): Boolean =
        !content.trimStart().substringBefore(' ').equals("/pay", ignoreCase = true)
}

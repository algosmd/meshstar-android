package net.meshstar

import net.meshstar.map.OsmTiles
import net.meshstar.meshcore.TileKey
import org.junit.Assert.*
import org.junit.Test

class MapTilePolicyTest {
    @Test fun requestsContainOnlyVisibleTileCoordinatesAndAppUserAgent(){
        val request=OsmTiles.request(TileKey(2,1,2))
        assertEquals("https",request.url.scheme)
        assertEquals("tile.openstreetmap.org",request.url.host)
        assertEquals("/2/1/2.png",request.url.encodedPath)
        assertNull(request.url.query);assertNull(request.body)
        assertEquals(setOf("User-Agent"),request.headers.names())
        assertTrue(request.header("User-Agent")!!.startsWith("MeshStar/"))
        assertNull(request.header("Authorization"));assertNull(request.header("Cache-Control"))
        assertTrue(runCatching{OsmTiles.request(TileKey(2,4,1))}.isFailure)
    }
}

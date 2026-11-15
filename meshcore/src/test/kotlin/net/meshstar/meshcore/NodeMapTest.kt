package net.meshstar.meshcore

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class NodeMapTest {
    private fun node(id:Int,lat:Int=10000000,lon:Int=20000000,at:Long=100,type:Int=2)=NodeContact(ByteArray(32){id.toByte()},"Synthetic $id",type,0,0,advertisedAt=at,advLat=lat,advLon=lon)
    @Test fun newestHiddenAdvertRemovesStaleLocationAndOwnNodeIsExcluded(){
        val old=node(1);val hidden=old.copy(advertisedAt=200,advLat=0,advLon=0)
        assertTrue(NodeMap.entries(listOf(old),listOf(hidden)).isEmpty())
        assertTrue(NodeMap.entries(listOf(old),emptyList(),old.key).isEmpty())
        assertTrue(NodeMap.entries(listOf(node(1,lat=91000000)),emptyList()).isEmpty())
        assertEquals(1,NodeMap.entries(listOf(old),listOf(old)).size)
    }
    @Test fun filtersRespectTypeUnknownAgeAndDayStaleness(){
        val entries=NodeMap.entries(listOf(node(1,at=100),node(2,at=0,type=4),node(3,at=86500,type=1)),emptyList())
        assertEquals(1,NodeMap.filter(entries,setOf(1,2,4),3600,87000).size)
        assertTrue(entries.first().stale(87000));assertEquals(.55f,entries.first().opacity(87000))
        assertEquals(1,NodeMap.filter(entries,setOf(4),null,87000).size)
        assertEquals(0,NodeMap.filter(entries,setOf(4),86400,87000).size)
    }
    @Test fun projectionWrapsDatelineAndTilesNeverLeaveWorldBounds(){
        val points=listOf(GeoPoint(1.0,179.9),GeoPoint(1.0,-179.9))
        val fitted=NodeMap.fit(points,720,1280,512f)
        assertTrue(abs(fitted.center.longitude)>179);assertTrue(fitted.zoom>=8)
        val screen=NodeMap.screen(points[0],fitted,720,1280,512f)
        assertTrue(screen.first in 0f..720f)
        for(v in listOf(fitted,MapViewport(GeoPoint(85.0,179.9),2))){
            val tiles=NodeMap.tiles(v,720,1280,512f)
            assertTrue(tiles.size in 1..80)
            assertTrue(tiles.all{it.key.x in 0 until (1 shl v.zoom)&&it.key.y in 0 until (1 shl v.zoom)})
        }
        val original=GeoPoint(-1.25,2.5);val p=NodeMap.project(original);val restored=NodeMap.inverse(p.first,p.second)
        assertEquals(original.latitude,restored.latitude,1e-7);assertEquals(original.longitude,restored.longitude,1e-7)
    }
    @Test fun invalidAndEmptyInputsNeverCreateNanViewport(){
        assertEquals(MapViewport(),NodeMap.fit(emptyList(),720,1280,512f))
        assertEquals(MapViewport(),NodeMap.fit(listOf(GeoPoint(Double.NaN,0.0)),720,1280,512f))
        assertTrue(NodeMap.tiles(MapViewport(),0,0,512f).isEmpty())
    }
}

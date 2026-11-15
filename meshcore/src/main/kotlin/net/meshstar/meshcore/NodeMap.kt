package net.meshstar.meshcore

import kotlin.math.*
import net.meshstar.rnslite.hex

data class GeoPoint(val latitude:Double,val longitude:Double) {
    val valid get()=latitude.isFinite()&&longitude.isFinite()&&latitude in -90.0..90.0&&longitude in -180.0..180.0
}
data class MapEntry(val contact:NodeContact) {
    val point get()=GeoPoint(contact.advLat/1e6,contact.advLon/1e6)
    fun age(now:Long):Long?=contact.advertisedAt.takeIf{it>0}?.let{max(0,now-it)}
    fun stale(now:Long)=(age(now)?:Long.MAX_VALUE)>=86400
    fun opacity(now:Long)=max(0.55,1-min((age(now)?:86400)/86400.0,1.0)*0.45).toFloat()
}
data class MapViewport(val center:GeoPoint=GeoPoint(0.0,0.0),val zoom:Int=2)
data class TileKey(val zoom:Int,val x:Int,val y:Int)
data class TilePlacement(val key:TileKey,val left:Float,val top:Float)
object NodeMap {
    fun entries(contacts:List<NodeContact>,adverts:List<NodeContact>,own:ByteArray?=null):List<MapEntry> {
        val newest=linkedMapOf<String,NodeContact>()
        (contacts+adverts).filterNot{own!=null&&it.key.contentEquals(own)}.forEach{c->
            val old=newest[c.key.hex()];if(old==null||c.advertisedAt>old.advertisedAt)newest[c.key.hex()]=c
        }
        return newest.values.map(::MapEntry).filter{it.point.valid&&(it.point.latitude!=0.0||it.point.longitude!=0.0)}.sortedBy{it.contact.key.hex()}
    }
    fun filter(entries:List<MapEntry>,types:Set<Int>,age:Long?,now:Long)=entries.filter{(if(it.contact.type in 1..4)it.contact.type else 0) in types&&(age==null||it.age(now)?.let{n->n<age}==true)}
    fun project(p:GeoPoint):Pair<Double,Double> {
        val latitude=p.latitude.coerceIn(-85.05112878,85.05112878)*PI/180
        return (p.longitude+180)/360 to (1-ln(tan(latitude)+1/cos(latitude))/PI)/2
    }
    fun inverse(x:Double,y:Double)=GeoPoint(atan(sinh(PI*(1-2*y.coerceIn(0.0,1.0))))*180/PI,((x%1+1)%1)*360-180)
    fun screen(p:GeoPoint,v:MapViewport,width:Int,height:Int,tileSize:Float):Pair<Float,Float>{
        val a=project(p);val c=project(v.center);val world=tileSize*2.0.pow(v.zoom)
        var dx=a.first-c.first;dx-=round(dx)
        return (width/2.0+dx*world).toFloat() to (height/2.0+(a.second-c.second)*world).toFloat()
    }
    fun pan(v:MapViewport,dx:Float,dy:Float,tileSize:Float):MapViewport {
        val c=project(v.center);val world=tileSize*2.0.pow(v.zoom)
        return v.copy(center=inverse(c.first-dx/world,c.second-dy/world))
    }
    fun tiles(v:MapViewport,width:Int,height:Int,tileSize:Float):List<TilePlacement>{
        if(width<=0||height<=0||tileSize<=0)return emptyList()
        val n=1 shl v.zoom;val c=project(v.center)
        val left=c.first*n-width/2.0/tileSize;val top=c.second*n-height/2.0/tileSize
        val list=mutableListOf<TilePlacement>()
        for(x in floor(left).toInt()..floor(left+width/tileSize).toInt())for(y in floor(top).toInt()..floor(top+height/tileSize).toInt()){
            if(y in 0 until n)list.add(TilePlacement(TileKey(v.zoom,((x%n)+n)%n,y),((x-left)*tileSize).toFloat(),((y-top)*tileSize).toFloat()))
        }
        return list.take(80)
    }
    fun fit(points:List<GeoPoint>,width:Int,height:Int,tileSize:Float):MapViewport {
        val p=points.filter{it.valid};if(p.isEmpty())return MapViewport()
        val xs=p.map{project(it).first}.sorted();val ys=p.map{project(it).second}
        var largest=-1.0;var start=xs[0]
        xs.indices.forEach{i->val next=if(i+1<xs.size)xs[i+1]else xs[0]+1;val gap=next-xs[i];if(gap>largest){largest=gap;start=next%1}}
        val spanX=1-largest;val spanY=ys.max()-ys.min()
        val scale=min(width/(max(spanX,0.00005)*tileSize),height/(max(spanY,0.00005)*tileSize))*0.7
        val zoom=floor(log2(scale.coerceAtLeast(1.0))).toInt().coerceIn(2,16)
        return MapViewport(inverse(start+spanX/2,(ys.min()+ys.max())/2),zoom)
    }
}

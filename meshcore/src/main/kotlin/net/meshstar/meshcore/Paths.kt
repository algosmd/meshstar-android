package net.meshstar.meshcore

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import net.meshstar.rnslite.hex
import net.meshstar.rnslite.unhex
import java.security.SecureRandom

data class ContactPath(val bytes:ByteArray,val width:Int) {
    init{valid(width in 1..3&&bytes.size<=64&&bytes.size%width==0&&bytes.size/width<=63)}
    val hashes get()=bytes.toList().chunked(width).map{it.toByteArray()}
    val encodedLength get()=(bytes.size/width) or ((width-1) shl 6)
    val text get()=hashes.joinToString(":"){it.hex()}
    fun same(other:ContactPath)=width==other.width&&bytes.contentEquals(other.bytes)
    fun apply(contact:NodeContact)=contact.copy(pathLength=encodedLength,path=bytes.copyOf(64))
    companion object {
        fun from(contact:NodeContact):ContactPath {
            valid(contact.pathLength!=255)
            val width=(contact.pathLength shr 6)+1;val length=(contact.pathLength and 63)*width
            valid(length<=contact.path.size)
            return ContactPath(contact.path.copyOf(length),width)
        }
        fun parse(input:String,width:Int):ContactPath {
            var text=input.trim();var size=width
            val suffix=text.substringAfterLast(':',"")
            if(suffix.length==1&&suffix in listOf("0","1","2")){size=suffix.toInt()+1;text=text.substringBeforeLast(':')}
            text=text.filterNot{it.isWhitespace()||it==':'||it==','}
            valid(text.length%2==0&&text.all{it in "0123456789abcdefABCDEF"})
            return ContactPath(if(text.isEmpty())byteArrayOf()else text.unhex(),size)
        }
        fun name(hash:ByteArray,known:List<NodeContact>)=known.filter{it.type==2&&it.key.take(hash.size)==hash.toList()}.singleOrNull()?.name
    }
}
data class DiscoveredPath(val prefix:ByteArray,val outbound:ContactPath,val inbound:ContactPath) {
    companion object { fun parse(b:ByteArray):DiscoveredPath {
        valid(b.size>=10&&b.u(0)==0x8d&&b.u(1)==0);var cursor=8
        fun read():ContactPath {valid(cursor<b.size&&b.u(cursor)!=255);val packed=b.u(cursor++);val width=(packed shr 6)+1;val size=(packed and 63)*width;valid(cursor+size<=b.size);val p=ContactPath(b.copyOfRange(cursor,cursor+size),width);cursor+=size;return p}
        val out=read();val back=read();valid(cursor==b.size)
        return DiscoveredPath(b.copyOfRange(2,8),out,back)
    } }
}
data class TraceResult(val tag:Long,val auth:Long,val hashes:List<ByteArray>,val snrs:List<Double>,val finalSnr:Double) {
    companion object {
        fun parse(b:ByteArray):TraceResult {
            valid(b.size>=13&&b.u(0)==0x89&&b.u(1)==0&&b.u(3)<=3)
            val width=1 shl b.u(3);val length=b.u(2);val count=length/width
            valid(length%width==0&&count<=63&&b.size==13+length+count)
            return TraceResult(b.word(4),b.word(8),(12 until 12+length step width).map{b.copyOfRange(it,it+width)},(12+length until 12+length+count).map{b[it]/4.0},b.last()/4.0)
        }
        fun command(hashes:List<ByteArray>,tag:Long,auth:Long):ByteArray {
            val width=hashes.firstOrNull()?.size?:throw NodeError(NodeError.Code.FRAME)
            valid(width in listOf(1,2,4,8)&&hashes.size<=63&&hashes.size*width<=64&&hashes.all{it.size==width})
            val mode=listOf(1,2,4,8).indexOf(width)
            return byteArrayOf(0x24)+little(tag)+little(auth)+byteArrayOf(mode.toByte())+hashes.fold(byteArrayOf()){a,b->a+b}
        }
        fun roundTrip(contact:NodeContact,known:List<NodeContact>):List<ByteArray> {
            val p=ContactPath.from(contact);val width=if(p.width==3)4 else p.width
            val out=p.hashes.map { hash->if(p.width!=3)hash else known.filter{it.type==2&&it.key.take(3)==hash.toList()}.singleOrNull()?.key?.copyOf(width)?:throw NodeError(NodeError.Code.PATH_TRACE) }.toMutableList()
            if(contact.type==2)out.add(contact.key.copyOf(width))
            if(out.isEmpty())throw NodeError(NodeError.Code.PATH_TRACE)
            return out+out.dropLast(1).reversed()
        }
    }
}
data class PathState(val contact:NodeContact?=null,val busy:Boolean=false,val trace:TraceResult?=null,val error:NodeError.Code?=null)
class PathController(private val send:suspend(ByteArray)->ByteArray,private val read:suspend(ByteArray)->NodeContact,
    private val publish:suspend(NodeContact)->Unit,private val known:()->List<NodeContact>,private val allowed:()->Boolean) {
    private val mutable=MutableStateFlow(PathState());val state=mutable.asStateFlow()
    private val exchange=RemoteExchange(send);private var epoch=0
    private val random=SecureRandom()
    fun close(){epoch++;exchange.cancel();mutable.value=PathState()}
    suspend fun open(key:ByteArray){close();mutable.value=PathState(NodeContact(key,"",0,0,0));refresh()}
    private suspend fun operation(body:suspend(NodeContact,Int)->NodeContact){
        if(!allowed()||state.value.busy)throw NodeError(NodeError.Code.BUSY)
        val key=state.value.contact?.key?:throw NodeError(NodeError.Code.OFFLINE);val version=epoch
        mutable.value=state.value.copy(busy=true,error=null)
        try{val current=read(key);check(version);val result=body(current,version);check(version);publish(result);mutable.value=state.value.copy(contact=result)}
        catch(e:CancellationException){throw e}
        catch(e:Exception){if(version==epoch)mutable.value=state.value.copy(error=(e as? NodeError)?.code?:NodeError.Code.FRAME)}
        finally{if(version==epoch)mutable.value=state.value.copy(busy=false)}
    }
    private fun check(version:Int){if(version!=epoch)throw CancellationException()}
    suspend fun refresh()=operation{c,_->c}
    private suspend fun write(c:NodeContact,path:ContactPath?,version:Int):NodeContact {
        check(version)
        send(path?.apply(c)?.updateCommand()?: (byteArrayOf(0x0d)+c.key));check(version)
        val actual=read(c.key);check(version)
        valid(if(path==null)actual.pathLength==255 else path.same(ContactPath.from(actual)))
        return actual
    }
    suspend fun set(path:ContactPath?)=operation{c,v->write(c,path,v)}
    suspend fun discover()=operation{c,v->
        if(known().count{it.key.take(6)==c.key.take(6)}!=1)throw NodeError(NodeError.Code.AMBIGUOUS)
        val b=exchange.perform(byteArrayOf(0x34,0)+c.key){runCatching{DiscoveredPath.parse(it).prefix.contentEquals(c.key.copyOf(6))}.getOrDefault(false)}
        check(v);write(read(c.key),DiscoveredPath.parse(b).outbound,v)
    }
    suspend fun trace()=operation{c,v->
        val hashes=TraceResult.roundTrip(c,known());val tag=random.nextInt().toLong() and 0xffffffff;val auth=random.nextInt().toLong() and 0xffffffff
        val b=exchange.perform(TraceResult.command(hashes,tag,auth),hashes.size){runCatching{val result=TraceResult.parse(it);result.tag==tag&&result.auth==auth&&result.hashes.size==hashes.size&&result.hashes.zip(hashes).all{(a,b)->a.contentEquals(b)}}.getOrDefault(false)}
        check(v);mutable.value=state.value.copy(trace=TraceResult.parse(b));c
    }
    fun receive(b:ByteArray){exchange.receive(b)}
}

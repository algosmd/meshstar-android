package net.meshstar.meshcore

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import net.meshstar.rnslite.hex
import java.util.Locale

object AdminProtocol {
    fun login(key:ByteArray,password:String):ByteArray {
        valid(key.size==32&&password.toByteArray().size<=15&&'\u0000' !in password)
        return byteArrayOf(0x1a)+key+password.toByteArray()
    }
    fun command(key:ByteArray,text:String,time:Long):ByteArray {
        valid(key.size==32&&text.toByteArray().size in 1..133&&text.none{it.isISOControl()})
        return byteArrayOf(2,1,0)+little(time)+key.copyOf(6)+text.toByteArray()
    }
    fun cli(b:ByteArray):Boolean {
        if(b.isEmpty()||b.u(0) !in setOf(7,0x10))return false
        val offset=if(b.u(0)==7)1 else 4
        return b.size>=offset+12&&b.u(offset+7) in setOf(1,3)
    }
    fun sensitive(text:String)=listOf("password","passwd","pwd","secret","priv","prv","pin").any{text.contains(it,true)}
    fun confirm(text:String)=text.trim().lowercase() !in setOf("time","clock","ver","neighbors","get radio","get name")
    fun radio(value:RadioParameters):String {valid(value.valid);return String.format(Locale.ROOT,"set radio %.3f,%.2f,%d,%d",value.frequency,value.bandwidth,value.sf,value.cr)}
    fun name(value:String):String {valid(value.toByteArray().size in 1..31&&value.none{it.isISOControl()||it in "[]\\:,?*"});return "set name $value"}
}
data class RepeaterStatus(val batteryMV:Int,val noise:Int,val rssi:Int,val received:Long,val sent:Long,val tx:Long,val uptime:Long,val rx:Long) {
    companion object { fun parse(b:ByteArray,key:ByteArray):RepeaterStatus {
        valid(b.size>=60&&b.u(0)==0x87&&key.size==32&&b.copyOfRange(2,8).contentEquals(key.copyOf(6)))
        fun short(i:Int)=(b.u(i) or (b.u(i+1) shl 8))
        return RepeaterStatus(short(8),short(12).toShort().toInt(),short(14).toShort().toInt(),b.word(16),b.word(20),b.word(24),b.word(28),b.word(56))
    } }
}
data class AdminState(val contact:NodeContact?=null,val loggedIn:Boolean=false,val busy:Boolean=false,
    val lines:List<String> = emptyList(),val status:RepeaterStatus?=null,val error:NodeError.Code?=null)
class RepeaterAdmin(private val scope:CoroutineScope,private val send:suspend(ByteArray)->ByteArray,
    private val unambiguous:(ByteArray)->Boolean,private val save:suspend(ByteArray,String)->Unit) {
    private val mutable=MutableStateFlow(AdminState());val state=mutable.asStateFlow()
    private val exchange=RemoteExchange(send)
    private var epoch=0;private var password="";private var timestamp=0L
    private val seen=linkedSetOf<String>()
    fun open(contact:NodeContact){close();valid(contact.type in setOf(2,3));mutable.value=AdminState(contact)}
    private fun check():NodeContact {
        val contact=state.value.contact?:throw NodeError(NodeError.Code.OFFLINE)
        if(!unambiguous(contact.key))throw NodeError(NodeError.Code.AMBIGUOUS)
        return contact
    }
    private suspend fun operation(body:suspend(NodeContact)->Unit){
        if(state.value.busy)throw NodeError(NodeError.Code.BUSY)
        val version=epoch;mutable.value=state.value.copy(busy=true,error=null)
        try{body(check())}
        catch(e:CancellationException){throw e}
        catch(e:Exception){if(epoch==version)mutable.value=state.value.copy(error=(e as? NodeError)?.code?:NodeError.Code.FRAME,loggedIn=false)}
        finally{if(epoch==version)mutable.value=state.value.copy(busy=false)}
    }
    suspend fun login(value:String,wait:Int=0)=operation{contact->
        val version=epoch
        password=value
        val b=exchange.perform(AdminProtocol.login(contact.key,value),contact.pathLength,wait){it.size>=8&&it.u(0) in setOf(0x85,0x86)&&it.copyOfRange(2,8).contentEquals(contact.key.copyOf(6))}
        if(b.u(0)!=0x85)throw NodeError(NodeError.Code.LOGIN_FAILED)
        if(b.u(1) and 1==0)throw NodeError(NodeError.Code.READ_ONLY)
        save(contact.key,value);if(epoch==version)mutable.value=state.value.copy(loggedIn=true)
    }
    suspend fun run(text:String,wait:Int=0)=operation{contact->
        val version=epoch
        valid(state.value.loggedIn)
        timestamp=maxOf(System.currentTimeMillis()/1000,timestamp+1)
        val frame=AdminProtocol.command(contact.key,text.trim(),timestamp)
        val sensitive=AdminProtocol.sensitive(text)
        append(if(sensitive)"[redacted]" else "> $text")
        if(text.trim().equals("reboot",true)){
            send(frame);if(version!=epoch)return@operation;mutable.value=state.value.copy(loggedIn=false);append("[reboot submitted]")
        }else{
            val b=exchange.perform(frame,contact.pathLength,wait){ f->
                if(!AdminProtocol.cli(f)||f.hex() in seen)false
                else Protocol.foreign(f).prefix?.contentEquals(contact.key.copyOf(6))==true
            }
            seen.add(b.hex());while(seen.size>256)seen.remove(seen.first())
            append(if(sensitive)"[redacted]" else Protocol.foreign(b).text)
        }
    }
    suspend fun status(wait:Int=0)=operation{contact->
        valid(state.value.loggedIn)
        val b=exchange.perform(byteArrayOf(0x1b)+contact.key,contact.pathLength,wait){it.size>=60&&it.u(0)==0x87&&it.copyOfRange(2,8).contentEquals(contact.key.copyOf(6))}
        mutable.value=state.value.copy(status=RepeaterStatus.parse(b,contact.key))
    }
    /** Swallow CLI traffic even after close: never persist it as a DM or notify. */
    fun receive(frame:ByteArray):Boolean {
        val admin=AdminProtocol.cli(frame)||frame.firstOrNull()?.let{(it.toInt() and 255) in setOf(0x85,0x86,0x87)}==true
        if(admin&&state.value.contact?.let{unambiguous(it.key)}==true)exchange.receive(frame)
        return admin
    }
    private fun append(line:String){val safe=if(password.isEmpty())line else line.replace(password,"••••");mutable.value=state.value.copy(lines=(state.value.lines+safe.take(4096)).takeLast(100))}
    fun close(logout:Boolean=true){val key=state.value.contact?.key;epoch++;exchange.cancel();password="";seen.clear();mutable.value=AdminState()
        if(logout&&key!=null)scope.launch{runCatching{send(byteArrayOf(0x1d)+key)}}
    }
}

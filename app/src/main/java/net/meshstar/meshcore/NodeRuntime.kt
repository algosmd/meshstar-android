package net.meshstar.meshcore

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import net.meshstar.data.SatEngine

/** Shares the existing foreground-service owner; no radio work outlives a stopped runtime. */
class NodeRuntime(context:Context,private val engine:SatEngine) {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val radio=MeshCoreBle(context.applicationContext,scope)
    val devices=radio.devices;val rssi=radio.rssi
    val state=MutableStateFlow(NodeState());val status=MutableStateFlow("node_off")
    val conflict=MutableStateFlow(false)
    private sealed interface Pending {
        data class Packet(val raw:ByteArray,val reception:Reception,val carrier:String):Pending
        data class Text(val value:ForeignText):Pending
    }
    private val pending=java.util.ArrayDeque<Pending>()
    private suspend fun flushPending(){
        while(pending.isNotEmpty()){
            val delivery=pending.first
            withContext(Dispatchers.IO){when(delivery){is Pending.Packet->engine.receiveRadio(delivery.raw,delivery.reception,delivery.carrier);is Pending.Text->engine.receiveForeign(delivery.value)}}
            pending.removeFirst()
        }
    }
    private suspend fun deliver(value:Pending){
        if(pending.size>=256)throw NodeError(NodeError.Code.STORAGE)
        pending.addLast(value);flushPending()
    }
    private var failure:Throwable?=null
    private var session:Session?=null;private var job:Job?=null;private var started=false
    init { radio.disconnected={error->failure=error?:NodeError(NodeError.Code.OFFLINE);session?.queue?.close(failure!!)} }
    fun scan(){try{radio.scan();status.value="node_scanning"}catch(_:Exception){status.value="node_permission"}}
    fun start(){started=true;engine.state.value.node?.let{connect(it)}}
    fun stop(){started=false;job?.cancel();session?.stop();session=null;radio.disconnect();state.value=NodeState();status.value="node_off"}
    suspend fun configure(id:String,name:String,slot:Int,secret:String,replace:Boolean){
        require(slot in 0..39)
        val old=engine.state.value.node
        val parsed=if(secret.isBlank())old?.takeIf{it.device==id}?.secret else channelSecret(secret)
        if(replace&&parsed==null)throw NodeError(NodeError.Code.SECRET)
        val config=NodeConfiguration(id,name,slot,parsed)
        job?.cancelAndJoin()
        withContext(Dispatchers.IO){engine.saveNode(config)};started=true;connect(config,replace)
    }
    suspend fun forget(){stop();job?.join();withContext(Dispatchers.IO){engine.saveNode(null)}}
    fun erase(){stop();pending.clear();scope.cancel()}
    private fun connect(initial:NodeConfiguration,replace:Boolean=false){
        val previous=job;previous?.cancel();conflict.value=false
        job=scope.launch{
            previous?.join()
            var backoff=2000L;var consent=replace
            while(isActive&&started){
                var receive:Job?=null;var updates:Job?=null
                try{
                    failure=null;flushPending()
                    status.value="node_connecting";radio.connect(initial.device)
                    val s=Session(this,radio::write,{failure=NodeError(NodeError.Code.OFFLINE);radio.disconnect()},
                        save={withContext(Dispatchers.IO){engine.saveNode(it)}},
                        packet={raw,reception->deliver(Pending.Packet(raw,reception,engine.state.value.node?.publicKey?:throw NodeError(NodeError.Code.OFFLINE)))},
                        foreign={text->deliver(Pending.Text(text))},
                        saveChannels={channels->withContext(Dispatchers.IO){engine.saveChannels(channels)}},
                        saveContacts={contacts->withContext(Dispatchers.IO){engine.saveNativeContacts(contacts)}})
                    session=s
                    receive=launch{for(frame in radio.frames){try{s.queue.receive(frame)}catch(e:CancellationException){throw e}catch(e:Exception){failure=e;s.queue.close(e);break}}}
                    updates=launch{s.state.collect{state.value=it}}
                    status.value="node_reading"
                    s.initialize(engine.state.value.node?:initial,consent);consent=false
                    status.value=if(s.state.value.ready)"node_ready"else "node_text_only";backoff=2000
                    while(isActive){
                        failure?.let{throw it}
                        withContext(Dispatchers.IO){engine.flushRadio { raw->withContext(Dispatchers.Main.immediate){s.send(raw)} }}
                        radio.readRssi();s.drain();delay(5000)
                    }

                }catch(e:CancellationException){throw e}
                catch(e:Exception){
                    consent=false;status.value=when((e as? NodeError)?.code){NodeError.Code.OCCUPIED->"node_occupied";NodeError.Code.SECRET->"node_secret";NodeError.Code.MTU->"node_mtu";else->"node_failed"}
                    if(e is NodeError&&e.code==NodeError.Code.OCCUPIED){conflict.value=true;break}
                }finally{receive?.cancel();updates?.cancel();session?.stop();session=null;radio.disconnect();state.value=NodeState()}
                delay(backoff);backoff=minOf(30000,backoff*2)
            }
        }
    }
    suspend fun advertise(){session?.advertise()?:throw NodeError(NodeError.Code.OFFLINE)}
    suspend fun refresh(){session?.refreshContacts()?:throw NodeError(NodeError.Code.OFFLINE)}
    private var lastTextTimestamp=0L
    private fun nextTimestamp():Long = maxOf(System.currentTimeMillis()/1000,lastTextTimestamp+1).also{lastTextTimestamp=it}
    suspend fun sendDirect(text:String,key:ByteArray):Long {val time=nextTimestamp();session?.sendDirect(text,key,time)?:throw NodeError(NodeError.Code.OFFLINE);return time}
    suspend fun sendText(text:String,channel:Int):Long {val time=nextTimestamp();session?.sendChannel(text,channel,time)?:throw NodeError(NodeError.Code.OFFLINE);return time}
    companion object {
        fun channelSecret(raw:String):String {
            val text=raw.trim()
            val key=if(text.startsWith("meshcore:")){
                val uri=android.net.Uri.parse(text)
                require(uri.host=="channel"&&uri.path=="/add"&&uri.getQueryParameters("name")==listOf("MESHSAT")&&uri.getQueryParameters("secret").size==1)
                uri.getQueryParameter("secret").orEmpty()
            }else text
            if(!key.matches(Regex("[0-9a-fA-F]{32}"))||key.all{it=='0'})throw NodeError(NodeError.Code.SECRET)
            return key.lowercase()
        }
    }
}

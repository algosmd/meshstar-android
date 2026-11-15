package net.meshstar.meshcore

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.meshstar.rnslite.*
import java.security.SecureRandom

data class NodeConfiguration(val device:String,val name:String,val slot:Int=7,val secret:String?=null,val publicKey:String?=null)
data class NodeState(val name:String="",val slot:Int=7,val ready:Boolean=false,val companion:Boolean=false,
                     val channels:List<Channel> = emptyList(),val contacts:List<NodeContact> = emptyList(),val advertisements:List<NodeContact> = emptyList(), val settings:NodeSettings?=null)
/** Android GATT is an adapter. This protocol session is exercised with fake notifications on JVM. */
class Session(private val scope:CoroutineScope,write:suspend(ByteArray)->Unit,private val disconnect:()->Unit,
              private val save:suspend(NodeConfiguration)->Unit,
              private val packet:suspend(ByteArray,Reception)->Unit,
              private val foreign:suspend(ForeignText)->Unit,
              private val saveChannels:suspend(List<Channel>)->Unit={},
              private val saveContacts:suspend(List<NodeContact>)->Unit={},
              private val saveAdmin:suspend(ByteArray,String)->Unit={_,_->},
              private val now:()->Long={System.nanoTime()/1_000_000},private val pause:suspend(Long)->Unit={delay(it)}) {
    val queue=CommandQueue(write,desynchronized=disconnect)
    val admin=RepeaterAdmin(scope,{queue.command(it,setOf(0,6))},{key->state.value.contacts.count{it.key.take(6)==key.take(6)}==1},saveAdmin)
    val paths=PathController({queue.command(it,setOf(0,6))},{key->
        val contact=NodeContact.parse(queue.command(byteArrayOf(30)+key,setOf(3)))
        valid(contact.key.contentEquals(key));contact
    },{contact->
        val contacts=state.value.contacts.filterNot{it.key.contentEquals(contact.key)}+contact
        saveContacts(contacts);mutable.value=state.value.copy(contacts=contacts)
    },{state.value.contacts},{state.value.companion&&!admin.state.value.busy&&!admin.state.value.loggedIn})
    private val mutable=MutableStateFlow(NodeState());val state=mutable.asStateFlow()
    private var configuration:NodeConfiguration?=null
    private val settingsEdits=Mutex()
    private val sends=Mutex();private var lastSent:Long?=null
    private var assembler=MeshCoreFrag.Assembler(now,45_000,256)
    private val completed=linkedMapOf<String,Long>()
    private var drainJob:Job?=null;private var waiting=false;private var cursor=0L
    private var contactJob:Job?=null
    private val advertKeys=linkedSetOf<String>()
    private val rng=SecureRandom()
    init {
        queue.onPush={b->paths.receive(b);if(!admin.receive(b))when(b.u(0)){
            0x83->{waiting=true;drain()}
            0x8a->{ val c=NodeContact.parse(b);mutable.value=mutable.value.copy(advertisements=(mutable.value.advertisements.filterNot{it.key.contentEquals(c.key)}+c).takeLast(256)) }
            0x80,0x81->{if(b.size==33){advertKeys.add(b.copyOfRange(1,33).hex());while(advertKeys.size>256)advertKeys.remove(advertKeys.first());refreshAdverts()}}
        }}
        queue.onMessage=message@{b->
            if(admin.receive(b))return@message
            if(b.u(0)==0x1b){
                val d=Protocol.datagram(b)
                if(state.value.ready&&d.channel==state.value.slot&&d.type==65535){
                    val f=runCatching{MeshCoreFrag.parse(d.bytes)}.getOrNull()
                    if(f!=null){
                        completed.entries.removeAll{now()-it.value>=90000};val key="${f.packetId}:${f.total}"
                        if(key !in completed)assembler.feed(d.bytes)?.let{raw->
                            valid(raw.size<=if(raw.take(5).toByteArray().contentEquals(VoiceFrame.magic))1400 else 500)
                            packet(raw,d.reception)
                            completed[key]=now();while(completed.size>512)completed.remove(completed.keys.first())
                        }
                    }
                }
            } else foreign(Protocol.foreign(b))
        }
    }
    suspend fun initialize(config:NodeConfiguration,replace:Boolean=false):NodeConfiguration {
        queue.open();val (name,key)=Protocol.self(queue.command(Protocol.appStart,setOf(5)))
        val count=Protocol.capacity(queue.command(byteArrayOf(0x16,3),setOf(0x0d)))
        val channels=(0 until count).map{Protocol.channel(queue.command(byteArrayOf(0x1f,it.toByte()),setOf(0x12),it),it)}.toMutableList()
        val (chosen,write)=Protocol.choose(channels,config.slot,config.secret?.unhex(),replace)
        if(write){
            queue.command(Protocol.setChannel(chosen.index,chosen.secret),setOf(0))
            val check=Protocol.channel(queue.command(byteArrayOf(0x1f,chosen.index.toByte()),setOf(0x12),chosen.index),chosen.index)
            valid(check.name=="MESHSAT"&&check.secret.contentEquals(chosen.secret));channels[chosen.index]=check
        }
        val ready=chosen.name=="MESHSAT"&&chosen.secret.any{it!=0.toByte()}
        val resolved=config.copy(name=name.ifEmpty{config.name},slot=chosen.index,secret=if(ready)chosen.secret.hex()else config.secret,publicKey=key.hex())
        configuration=resolved
        save(resolved) // Secret stays in encrypted app storage; exposed channel rows omit it.
        mutable.value=NodeState(resolved.name,chosen.index,ready,true,channels.map{it.copy(secret=byteArrayOf())})
        saveChannels(mutable.value.channels)
        refreshContacts();refreshAdverts();waiting=true;drain();return resolved
    }
    suspend fun refreshContacts(){val (contacts,end)=queue.contacts(if(cursor==0L)null else cursor);cursor=end
        val byKey=mutable.value.contacts.associateBy{it.key.hex()}.toMutableMap();contacts.forEach{byKey[it.key.hex()]=it}
        saveContacts(byKey.values.toList())
        mutable.value=mutable.value.copy(contacts=byKey.values.toList())
    }
    private fun refreshAdverts(){if(contactJob?.isActive==true)return
        contactJob=scope.launch{while(advertKeys.isNotEmpty()&&state.value.companion){
            val key=advertKeys.first();advertKeys.remove(key)
            val c=runCatching{NodeContact.parse(queue.command(byteArrayOf(30)+key.unhex(),setOf(3)))}.getOrElse{NodeContact(key.unhex(),key.take(8),0,0,0)}
            mutable.value=mutable.value.copy(advertisements=(mutable.value.advertisements.filterNot{it.key.contentEquals(c.key)}+c).takeLast(256))
            if(c.type!=0&&state.value.contacts.any{it.key.contentEquals(c.key)}){
                val contacts=state.value.contacts.map{if(it.key.contentEquals(c.key))c else it};saveContacts(contacts);mutable.value=state.value.copy(contacts=contacts)
            }
        }}
    }
    fun drain(){if(!state.value.companion||drainJob?.isActive==true)return
        drainJob=scope.launch{try{do{waiting=false;val b=queue.command(byteArrayOf(10),Protocol.messages+10);if(b.u(0)==10&&!waiting)break}while(isActive&&state.value.companion)}catch(e:CancellationException){throw e}catch(e:Exception){queue.close(e);disconnect();stop()}}
    }
    suspend fun refreshSettings() {
        if(!state.value.companion)throw NodeError(NodeError.Code.OFFLINE)
        val snapshot=NodeSettings.parse(queue.command(Protocol.appStart,setOf(5)))
        val device=queue.command(byteArrayOf(0x16,3),setOf(0x0d))
        valid(device.size>=2)
        val settings=snapshot.copy(firmware=if(device.size>=80)device.text(60,80) else "v"+device.u(1),repeat=if(device.size>80)device.u(80)!=0 else null,
            batteryMV=state.value.settings?.batteryMV,measurements=state.value.settings?.measurements.orEmpty())
        configuration?.copy(name=settings.name)?.let{save(it);configuration=it}
        mutable.value=state.value.copy(name=settings.name,settings=settings)
    }
    suspend fun refreshTelemetry(){
        val current=state.value.settings?:throw NodeError(NodeError.Code.OFFLINE)
        val battery=queue.command(byteArrayOf(0x14),setOf(0x0c));valid(battery.size>=3)
        mutable.value=state.value.copy(settings=current.copy(batteryMV=battery.u(1) or (battery.u(2) shl 8)))
        val telemetry=queue.command(byteArrayOf(0x27,0,0,0),setOf(0x8b),matching={it.size>=8&&it.copyOfRange(2,8).contentEquals(current.key.copyOf(6))})
        mutable.value=state.value.copy(settings=state.value.settings?.copy(measurements=LocalTelemetry.parse(telemetry,current.key)))
    }
    suspend fun changeSettings(change:NodeChange)=settingsEdits.withLock {
        refreshSettings()
        queue.command(change.frame(state.value.settings!!),setOf(0))
        refreshSettings();valid(change.confirmed(state.value.settings!!))
    }
    suspend fun sendAdvert(flood:Boolean){queue.command(if(flood)byteArrayOf(7,1)else byteArrayOf(7),setOf(0))}
    private val channelEdits = Mutex()
    private suspend fun readChannel(index: Int) = Protocol.channel(queue.command(byteArrayOf(0x1f, index.toByte()), setOf(0x12), index), index)
    suspend fun openHashtag(input: String, slot: Int? = null, expected: ByteArray? = null): String = channelEdits.withLock {
        val name = Hashtag.name(input)
        if (!state.value.companion) throw NodeError(NodeError.Code.OFFLINE)
        val channels = state.value.channels.map { readChannel(it.index) }
        channels.firstOrNull { it.name == name && it.secret.contentEquals(Hashtag.key(name)) }?.let { publishChannels(channels); return@withLock name }
        val target = if (slot == null) channels.firstOrNull { it.empty } ?: throw NodeError(NodeError.Code.CHANNEL_FULL)
            else channels.firstOrNull { it.index == slot } ?: throw NodeError(NodeError.Code.FRAME)
        valid(!target.protected)
        if (!target.empty && (expected == null || !target.fingerprint.contentEquals(expected))) throw NodeError(NodeError.Code.CHANNEL_CHANGED)
        queue.command(Protocol.writeChannel(target.index, name, Hashtag.key(name)), setOf(0))
        val check = readChannel(target.index)
        valid(check.name == name && check.secret.contentEquals(Hashtag.key(name)))
        publishChannels(channels.map { if (it.index == check.index) check else it })
        name
    }
    suspend fun leaveChannel(index: Int, expected: ByteArray) = channelEdits.withLock {
        val current = readChannel(index)
        valid(!current.protected)
        if (!current.fingerprint.contentEquals(expected)) throw NodeError(NodeError.Code.CHANNEL_CHANGED)
        queue.command(Protocol.writeChannel(index, "", ByteArray(16)), setOf(0))
        val check = readChannel(index); valid(check.empty)
        publishChannels(state.value.channels.map { if (it.index == index) check else it })
    }
    private suspend fun publishChannels(channels: List<Channel>) {
        val safe = channels.map { it.copy(secret = byteArrayOf()) }
        saveChannels(safe); mutable.value = state.value.copy(channels = safe)
    }
    suspend fun send(raw:ByteArray)=sends.withLock{
        if(!state.value.ready)throw NodeError(NodeError.Code.OFFLINE)
        valid(raw.size in 1..if(raw.take(5).toByteArray().contentEquals(VoiceFrame.magic))1400 else 500)
        for(f in MeshCoreFrag.split(raw,rng.nextInt(65536)))transmit(Protocol.sendData(f,state.value.slot),setOf(0))
    }
    suspend fun sendChannel(text:String,index:Int,timestamp:Long)=sends.withLock{
        val c=state.value.channels.firstOrNull{it.index==index}?:throw NodeError(NodeError.Code.FRAME)
        valid(!c.transport);transmit(Protocol.channelText(text,index,timestamp,state.value.name),setOf(0,6))
    }
    suspend fun sendDirect(text:String,key:ByteArray,timestamp:Long)=sends.withLock{
        valid(state.value.contacts.count{it.key.take(6)==key.take(6)}==1)
        transmit(Protocol.directText(text,key,timestamp),setOf(6))
    }
    suspend fun advertise()=sends.withLock{transmit(byteArrayOf(7,0),setOf(0))}
    private suspend fun transmit(frame:ByteArray,codes:Set<Int>){
        if(!state.value.companion)throw NodeError(NodeError.Code.OFFLINE)
        lastSent?.let{val remaining=1500-(now()-it);if(remaining>0)pause(remaining)}
        try{
            var retries=0
            while(true){try{queue.command(frame,codes);break}catch(e:NodeError){
                if(frame.first()!=0x3e.toByte()||e.code!=NodeError.Code.REJECTED||e.detail!=3||retries>=3)throw e
                retries++;pause(1500L*retries)
            }}
        }finally{lastSent=now()}
    }
    fun stop(){paths.close();admin.close(false);drainJob?.cancel();contactJob?.cancel();queue.close();mutable.value=NodeState();assembler=MeshCoreFrag.Assembler(now,45_000,256);completed.clear()}
}

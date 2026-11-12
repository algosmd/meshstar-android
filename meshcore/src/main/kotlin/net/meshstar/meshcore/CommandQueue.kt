package net.meshstar.meshcore

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One command on the wire. A timed-out/cancelled command poisons the connection:
 * without transaction IDs a late OK must never complete the next request. */
class CommandQueue(private val write: suspend (ByteArray)->Unit, private val timeout: Long=8000,
                   private val desynchronized: ()->Unit = {}) {
    private val serial=Mutex()
    private val lock=Any()
    private data class Pending(val accepts:(ByteArray)->Boolean,val intermediate:((ByteArray)->Boolean)?,val done:CompletableDeferred<ByteArray>,val activity:Channel<Unit> = Channel(Channel.CONFLATED))
    private var pending: Pending?=null
    private var open=false
    var onPush: suspend (ByteArray)->Unit={}
    var onMessage: suspend (ByteArray)->Unit={}
    fun open() { close(); synchronized(lock) { open=true } }
    fun close(error:Throwable=NodeError(NodeError.Code.OFFLINE)) {
        synchronized(lock) { open=false; pending?.done?.completeExceptionally(error);pending=null }
    }
    suspend fun command(frame:ByteArray,codes:Set<Int>,channel:Int?=null,intermediate:((ByteArray)->Boolean)?=null):ByteArray = serial.withLock {
        val p=Pending({ b->b.isNotEmpty() && b.u(0) in codes && (channel==null || b.u(0)!=0x12 || (b.size>1&&b.u(1)==channel)) },intermediate,CompletableDeferred())
        synchronized(lock) { if(!open)throw NodeError(NodeError.Code.OFFLINE); pending=p }
        try {
            withTimeout(timeout) { write(frame) }
            var response:ByteArray?=null
            while(response==null)response=withTimeout(timeout){select<ByteArray?>{
                p.done.onAwait{it};p.activity.onReceive{null}
            }}
            response
        }
        catch(e:Throwable) {
            if(e !is NodeError || e.code!=NodeError.Code.REJECTED) { close(e);desynchronized() }
            if(e is TimeoutCancellationException)throw NodeError(NodeError.Code.TIMEOUT)
            throw e
        } finally { synchronized(lock) { if(pending===p)pending=null } }
    }
    suspend fun receive(b:ByteArray) {
        if(b.isEmpty() || !synchronized(lock){open})return
        val code=b.u(0)
        if(code>=0x80){ onPush(b);return }
        if(code in Protocol.messages)onMessage(b) // Persist before next destructive SYNC.
        val p=synchronized(lock){pending}?:return
        try {
            if(p.intermediate?.invoke(b)==true){p.activity.trySend(Unit);return}
            if(code==1)p.done.completeExceptionally(NodeError(NodeError.Code.REJECTED,if(b.size>1)b.u(1) else 0))
            else if(p.accepts(b))p.done.complete(b)
        } catch(e:Throwable){close(e);desynchronized()}
    }
    suspend fun contacts(since:Long?=null):Pair<List<NodeContact>,Long> {
        val list=mutableListOf<NodeContact>();var count:Int?=null;var cursor=since?:0
        command(byteArrayOf(4)+(since?.let(::little)?:byteArrayOf()),setOf(4),intermediate={b->
            when(b.u(0)) {
                2->{valid(b.size==5&&count==null);val n=b.word(1);valid(n<=10000);count=n.toInt();true}
                3->{valid(count!=null&&list.size<count!!);val c=NodeContact.parse(b);valid(list.none{it.key.contentEquals(c.key)});list.add(c);true}
                4->{valid(b.size==5&&count!=null);val end=b.word(1);valid(list.all{it.modified<=end});cursor=maxOf(cursor,end);false}
                else->false
            }
        })
        return list to cursor
    }
}

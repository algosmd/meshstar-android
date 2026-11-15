package net.meshstar.meshcore

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** RF replies are awaited outside the UART queue, so SYNC continues while waiting. */
class RemoteExchange(private val send:suspend(ByteArray)->ByteArray) {
    private val serial=Mutex()
    private var pending:Pair<(ByteArray)->Boolean,CompletableDeferred<ByteArray>>?=null
    suspend fun perform(frame:ByteArray,pathLength:Int=255,seconds:Int=0,matches:(ByteArray)->Boolean):ByteArray=serial.withLock {
        val reply=CompletableDeferred<ByteArray>();pending=matches to reply
        try{
            val ack=withTimeout(20000){send(frame)}
            withTimeout(waitSeconds(ack,pathLength,seconds)*1000L){reply.await()}
        }catch(e:TimeoutCancellationException){throw NodeError(NodeError.Code.RADIO_TIMEOUT)}
        finally{if(pending?.second===reply)pending=null}
    }
    fun receive(b:ByteArray){pending?.let{if(it.first(b))it.second.complete(b)}}
    fun cancel(){pending?.second?.cancel();pending=null}
    companion object {
        fun waitSeconds(ack:ByteArray,path:Int,selected:Int=0):Int {
            if(selected in setOf(60,120,300))return selected
            val estimate=if(ack.size==10&&ack.u(0)==6)((ack.word(6)*2+999)/1000+5).coerceAtMost(300).toInt()else 0
            val flood=path==255||(ack.size==10&&ack.u(1)==1)
            return maxOf(if(flood)60 else 20+(path and 63)*10,estimate).coerceAtMost(300)
        }
    }
}

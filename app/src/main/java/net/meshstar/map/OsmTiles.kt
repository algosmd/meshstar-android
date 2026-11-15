package net.meshstar.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import net.meshstar.meshcore.TileKey
import okhttp3.*
import java.io.File
import java.io.IOException
import java.net.Proxy
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException

/** Only visible tiles; no identifiers, credentials, node data or location payloads. */
class OsmTiles(context:Context) {
    private val slots=Semaphore(4)
    private val client=OkHttpClient.Builder().proxy(Proxy.NO_PROXY).followRedirects(false).followSslRedirects(false)
        .cache(Cache(File(context.cacheDir,"meshstar-osm"),32L*1024*1024))
        .callTimeout(15,TimeUnit.SECONDS).build()
    suspend fun load(tile:TileKey):Bitmap?=slots.withPermit {
        val request=request(tile)
        val call=client.newCall(request)
        val response=suspendCancellableCoroutine<Response>{continuation->
            continuation.invokeOnCancellation{call.cancel()}
            call.enqueue(object:Callback{
                override fun onFailure(call:Call,e:IOException){if(continuation.isActive)continuation.resumeWithException(e)}
                override fun onResponse(call:Call,response:Response){continuation.resume(response){_,value,_->value.close()}}
            })
        }
        response.use{r->withContext(Dispatchers.IO){
            if(!r.isSuccessful)return@withContext null
            val body=r.body?:return@withContext null
            if(body.contentLength()>512*1024)return@withContext null
            val bytes=body.byteStream().use{stream->
                val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
                while(out.size()<=512*1024){val n=stream.read(buffer,0,minOf(buffer.size,512*1024+1-out.size()));if(n<0)break;out.write(buffer,0,n)}
                out.toByteArray()
            }
            if(bytes.size>512*1024)return@withContext null
            val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
            if(bounds.outWidth!=256||bounds.outHeight!=256)return@withContext null
            BitmapFactory.decodeByteArray(bytes,0,bytes.size)
        }}
    }
    companion object {
        fun request(tile:TileKey):Request {
            require(tile.zoom in 0..19&&tile.x in 0 until (1 shl tile.zoom)&&tile.y in 0 until (1 shl tile.zoom))
            return Request.Builder().url("https://tile.openstreetmap.org/${tile.zoom}/${tile.x}/${tile.y}.png")
                .header("User-Agent","MeshStar/"+com.bitchat.android.BuildConfig.VERSION_NAME+" (Android; https://meshstar.net)").build()
        }
    }
    fun close(){client.dispatcher.cancelAll();client.connectionPool.evictAll();client.cache?.close();client.dispatcher.executorService.shutdown()}
}

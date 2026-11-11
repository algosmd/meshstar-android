package net.meshstar.box

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class HttpFailure(val code: Int, val powBits: Int? = null, val retrySeconds: Long = 5) : IOException("HTTP $code")

/** No redirects, credentials in URLs, cookies, cache, logging or trust-all TLS. */
class CarrierHttp(private val client: OkHttpClient = OkHttpClient.Builder()
    .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
    .connectTimeout(15, TimeUnit.SECONDS).readTimeout(40, TimeUnit.SECONDS).callTimeout(45, TimeUnit.SECONDS).build()) {
    companion object {
        fun endpoint(value: String): HttpUrl {
            val url = value.trim().toHttpUrlOrNull() ?: error("Invalid URL")
            require(url.username.isEmpty() && url.password.isEmpty() && url.query == null && url.fragment == null) { "Use a URL without credentials or query" }
            return url
        }
    }
    suspend fun request(base: String, path: String, credential: String? = null, body: ByteArray? = null,
                        json: Boolean = true, query: Map<String, String> = emptyMap()): String {
        val builder = endpoint(base).newBuilder().addPathSegments(path)
        query.forEach { (name, value) -> builder.addQueryParameter(name, value) }
        val req = Request.Builder().url(builder.build()).header("Cache-Control", "no-store")
        credential?.let { req.header("X-Box-Token", it) }
        body?.let { req.post(it.toRequestBody((if (json) "application/json" else "application/octet-stream").toMediaType())) }
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(req.build())
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { if (!continuation.isCancelled) continuation.resumeWithException(e) }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val result = response.use {
                            val source = it.body.source()
                            require(it.body.contentLength() <= 4_000_000) { "Response too large" }
                            val buffer = okio.Buffer()
                            while (buffer.size <= 4_000_000 && source.read(buffer, minOf(8192, 4_000_001 - buffer.size)) != -1L) { /* bounded */ }
                            val data = buffer.readByteArray()
                            require(data.size <= 4_000_000) { "Response too large" }
                            val text = data.toString(Charsets.UTF_8)
                            if (!it.isSuccessful) {
                                val detail = runCatching { JsonParser.parseString(text).asJsonObject.getAsJsonObject("detail") }.getOrNull()
                                val bits = if (it.code == 429 && detail?.get("error")?.asString == "stamp required") detail.get("pow_bits")?.asInt else null
                                throw HttpFailure(it.code, bits, it.header("Retry-After")?.toLongOrNull()?.coerceIn(1, 300) ?: 5)
                            }
                            text
                        }
                        if (!continuation.isCancelled) continuation.resume(result)
                    } catch (e: Exception) { if (!continuation.isCancelled) continuation.resumeWithException(e) }
                }
            })
        }
    }
    suspend fun post(base: String, path: String, body: JsonObject): String = request(base, path, body = body.toString().toByteArray())
}

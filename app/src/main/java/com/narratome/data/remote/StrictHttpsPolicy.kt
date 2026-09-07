package com.narratome.data.remote

import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.ForwardingSource
import okio.buffer
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class HttpsRequiredException : IOException(
    "Require HTTPS is enabled. Update HTTP endpoints in Server settings or turn off Require HTTPS.",
)

/** Shared by all HTTP consumers; network interception also checks redirected destinations. */
class StrictHttpsPolicy(initiallyRequired: Boolean = false) : Interceptor {
    @Volatile var required: Boolean = initiallyRequired
        private set
    private val active = ConcurrentHashMap<Call, AtomicBoolean>()

    fun update(required: Boolean) {
        this.required = required
        if (required) active.forEach { (call, blocked) ->
            blocked.set(true)
            call.cancel()
        }
    }

    fun check(url: HttpUrl) {
        if (required && !url.isHttps) throw HttpsRequiredException()
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val url = chain.request().url
        if (url.isHttps) return chain.proceed(chain.request())
        val call = chain.call()
        val blocked = AtomicBoolean(false)
        active[call] = blocked
        try {
            check(url)
            val response = chain.proceed(chain.request())
            val body = response.body ?: run {
                active.remove(call, blocked)
                return response
            }
            val source = object : ForwardingSource(body.source()) {
                override fun read(sink: Buffer, byteCount: Long): Long {
                    try {
                        if (blocked.get()) throw HttpsRequiredException()
                        check(url)
                        return super.read(sink, byteCount)
                    } catch (error: IOException) {
                        if (blocked.get()) throw HttpsRequiredException()
                        throw error
                    }
                }
                override fun close() {
                    try { super.close() } finally { active.remove(call, blocked) }
                }
            }.buffer()
            return response.newBuilder().body(object : ResponseBody() {
                override fun contentType() = body.contentType()
                override fun contentLength() = body.contentLength()
                override fun source() = source
            }).build()
        } catch (error: IOException) {
            active.remove(call, blocked)
            if (blocked.get()) throw HttpsRequiredException()
            throw error
        }
    }
}

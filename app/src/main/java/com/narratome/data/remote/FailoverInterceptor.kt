package com.narratome.data.remote

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FailoverInterceptor @Inject constructor(private val resolver: ServerBaseUrlResolver) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val probe = request.tag(okhttp3.HttpUrl::class.java)
        if (probe != null && probe !in resolver.configured()) throw IOException("Unknown server endpoint")
        val base = probe ?: resolver.selected() ?: throw IOException("Server URL is not configured")
        val rewritten = serverUrl(request.url, base, resolver.configured())
            ?: return chain.proceed(request)
        val alternate = if (probe == null) resolver.alternate(base) else null
        val attempt = chain.withConnectTimeout(3, TimeUnit.SECONDS).let {
            if (rewritten.encodedPath.endsWith("/ping")) it.withReadTimeout(4, TimeUnit.SECONDS) else it
        }
        val response = try {
            attempt.proceed(request.newBuilder().url(rewritten).build())
        } catch (error: IOException) {
            if (alternate == null || chain.call().isCanceled()) throw error
            null
        }
        if (response != null && (response.code !in setOf(502, 503, 504) || alternate == null)) {
            if (response.isSuccessful) resolver.succeeded(base)
            return response
        }
        response?.close()
        val backup = alternate ?: throw IOException("Server unavailable")
        val retryUrl = serverUrl(request.url, backup, resolver.configured()) ?: throw IOException("Invalid server endpoint")
        return attempt.proceed(request.newBuilder().url(retryUrl).build()).also {
            if (it.isSuccessful) resolver.succeeded(backup)
        }
    }
}

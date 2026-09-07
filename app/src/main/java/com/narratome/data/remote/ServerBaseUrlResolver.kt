package com.narratome.data.remote

import com.narratome.domain.model.EndpointMode
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServerBaseUrlResolver internal constructor(private val snapshot: () -> ServerEndpointPrefsSnapshot) {
    @Inject constructor(endpointCache: ServerEndpointCache) : this(endpointCache::current)
    private var working: HttpUrl? = null
    private var config = ServerEndpointPrefsSnapshot.Initial

    @Synchronized
    fun reset() { working = null }

    @Synchronized
    fun resolveBlocking(): String? = selected()?.toString()?.trimEnd('/')

    @Synchronized
    fun selected(): HttpUrl? {
        val current = snapshot()
        if (current != config) { config = current; working = null }
        return when (current.endpointMode) {
            EndpointMode.PRIMARY -> current.primaryUrl.toHttpUrlOrNull()
            EndpointMode.SECONDARY -> current.secondaryUrl.toHttpUrlOrNull()
            EndpointMode.AUTO -> working ?: current.primaryUrl.toHttpUrlOrNull()
                ?: current.secondaryUrl.toHttpUrlOrNull()
        }
    }

    fun configured(): List<HttpUrl> = snapshot().let {
        listOfNotNull(it.primaryUrl.toHttpUrlOrNull(), it.secondaryUrl.toHttpUrlOrNull()).distinct()
    }

    fun alternate(base: HttpUrl): HttpUrl? =
        if (snapshot().endpointMode == EndpointMode.AUTO) configured().firstOrNull { it != base }
        else null

    @Synchronized
    fun succeeded(base: HttpUrl) {
        selected()
        if (config.endpointMode == EndpointMode.AUTO && base in configured()) working = base
    }

    fun toHttpUrlOrNull(): HttpUrl? = selected()
}

internal fun HttpUrl.belongsTo(base: HttpUrl): Boolean =
    scheme == base.scheme && host == base.host && port == base.port &&
        (base.encodedPath == "/" || encodedPath == base.encodedPath.trimEnd('/') ||
            encodedPath.startsWith(base.encodedPath.trimEnd('/') + "/"))

internal fun serverUrl(url: HttpUrl, base: HttpUrl, configured: List<HttpUrl>): HttpUrl? {
    val placeholder = url.scheme == "https" && url.host == "127.0.0.1" && url.port == 443
    val source = configured.sortedByDescending { it.encodedPath.length }.firstOrNull { url.belongsTo(it) }
    if (!placeholder && source == null) return null
    val relative = if (placeholder) url.encodedPath else url.encodedPath.removePrefix(source!!.encodedPath.trimEnd('/'))
    return url.newBuilder().scheme(base.scheme).host(base.host).port(base.port)
        .encodedPath(base.encodedPath.trimEnd('/') + "/" + relative.trimStart('/')).build()
}

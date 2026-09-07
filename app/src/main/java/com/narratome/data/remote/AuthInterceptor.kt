package com.narratome.data.remote

import com.narratome.data.local.auth.TokenStore
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

class AuthInterceptor internal constructor(
    private val token: () -> String?,
    private val resolver: ServerBaseUrlResolver,
) : Interceptor {
    @Inject constructor(tokenStore: TokenStore, resolver: ServerBaseUrlResolver) :
        this(tokenStore::getToken, resolver)

    fun isServerUrl(url: okhttp3.HttpUrl): Boolean = resolver.configured().any { url.belongsTo(it) }

    fun checkDestination(request: okhttp3.Request) {
        if (!isServerUrl(request.url) &&
            (request.header("Authorization") != null || request.url.queryParameter("token") != null)) {
            throw java.io.IOException("Refusing credentials outside configured server endpoints")
        }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        if (!isServerUrl(chain.request().url)) {
            if (chain.request().url.queryParameter("token") != null) {
                throw java.io.IOException("Refusing token outside configured server endpoints")
            }
            return chain.proceed(chain.request().newBuilder().removeHeader("Authorization").build())
        }
        val path = chain.request().url.encodedPath
        val public = path == "/login" || path == "/ping" || path == "/status" || path == "/init"
        if (public) {
            return chain.proceed(chain.request())
        }
        val token = token()
        val req = if (token.isNullOrBlank()) {
            chain.request()
        } else {
            chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        }
        return chain.proceed(req)
    }
}

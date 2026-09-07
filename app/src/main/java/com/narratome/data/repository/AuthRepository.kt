package com.narratome.data.repository

import com.narratome.data.local.auth.TokenStore
import com.narratome.data.remote.AudiobookshelfApi
import com.narratome.data.remote.dto.LoginRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val api: AudiobookshelfApi,
    private val tokenStore: TokenStore,
) {

    suspend fun ping(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { api.ping() }.map { }
    }

    suspend fun login(username: String, password: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val res = api.login(LoginRequest(username, password))
            val token = res.user?.token ?: error("No token")
            val user = res.user.username ?: username
            tokenStore.setSession(user, token)
        }
    }

    suspend fun authorize(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val res = api.authorize()
            val token = res.user?.token ?: error("No token")
            val user = res.user.username ?: error("No user")
            tokenStore.setSession(user, token)
        }
    }

}

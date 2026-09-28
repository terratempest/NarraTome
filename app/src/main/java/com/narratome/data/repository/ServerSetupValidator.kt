package com.narratome.data.repository

import com.narratome.data.remote.AudiobookshelfApi
import com.narratome.data.remote.HttpsRequiredException
import com.narratome.data.remote.StrictHttpsPolicy
import com.narratome.data.remote.dto.LoginRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class AuthenticatedServerSession(
    val username: String,
    val token: String,
)

class ServerSetupValidationException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

/** Checks first-run draft endpoints directly, without saving them or using the app's token. */
@Singleton
class ServerSetupValidator @Inject constructor(
    private val json: Json,
    private val httpsPolicy: StrictHttpsPolicy,
) {
    suspend fun validate(
        primary: HttpUrl,
        backup: HttpUrl?,
        username: String,
        password: String,
        existingToken: String? = null,
        requireHttps: Boolean = false,
    ): AuthenticatedServerSession {
        if (existingToken.isNullOrBlank() && (username.isBlank() || password.isBlank())) {
            throw ServerSetupValidationException("Enter your username and password to continue.")
        }

        val endpoints = listOfNotNull("Primary" to primary, backup?.let { "Backup" to it })
        endpoints.forEach { (label, endpoint) ->
            try {
                val response = withTimeout(ENDPOINT_TIMEOUT_MS) { apiAt(endpoint, requireHttps).ping() }
                if (response.success == false) {
                    throw ServerSetupValidationException("$label server did not pass its health check.")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: ServerSetupValidationException) {
                throw error
            } catch (error: HttpsRequiredException) {
                throw ServerSetupValidationException("Require HTTPS is on. Change the $label URL to HTTPS.", error)
            } catch (error: Exception) {
                throw ServerSetupValidationException(
                    "$label server could not be reached. Check the address and network.",
                    error,
                )
            }
        }

        val response = try {
            withTimeout(ENDPOINT_TIMEOUT_MS) {
                val api = apiAt(primary, requireHttps)
                if (existingToken.isNullOrBlank()) {
                    api.login(LoginRequest(username.trim(), password))
                } else {
                    api.authorize("Bearer $existingToken")
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: HttpException) {
            val message = if (error.code() == 401 || error.code() == 403) {
                if (existingToken.isNullOrBlank()) {
                    "Username or password was not accepted by the primary server."
                } else {
                    "Saved credentials were rejected. Enter your username and password to continue."
                }
            } else {
                "Credential check failed. Confirm the account and primary server."
            }
            throw ServerSetupValidationException(message, error)
        } catch (error: HttpsRequiredException) {
            throw ServerSetupValidationException("Require HTTPS is on. Change the primary URL to HTTPS.", error)
        } catch (error: Exception) {
            throw ServerSetupValidationException("Credentials could not be checked against the primary server.", error)
        }

        val user = response.user
            ?: throw ServerSetupValidationException("The primary server returned no user session.")
        val token = user.token?.takeIf(String::isNotBlank)
            ?: throw ServerSetupValidationException("The primary server returned no session token.")
        val resolvedUsername = user.username?.takeIf(String::isNotBlank)
            ?: username.trim().takeIf(String::isNotBlank)
            ?: throw ServerSetupValidationException("The primary server returned no username.")

        return AuthenticatedServerSession(resolvedUsername, token)
    }

    private fun apiAt(endpoint: HttpUrl, requireHttps: Boolean): AudiobookshelfApi {
        val requireHttpsGuard = Interceptor { chain ->
            if (requireHttps && !chain.request().url.isHttps) throw HttpsRequiredException()
            chain.proceed(chain.request())
        }
        val client = OkHttpClient.Builder()
            .connectTimeout(ENDPOINT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(ENDPOINT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .writeTimeout(ENDPOINT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .addInterceptor(httpsPolicy)
            .addInterceptor(requireHttpsGuard)
            .addNetworkInterceptor(httpsPolicy)
            .addNetworkInterceptor(requireHttpsGuard)
            .build()
        val baseUrl = endpoint.toString().let { if (it.endsWith('/')) it else "$it/" }
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory(JSON_MEDIA_TYPE))
            .build()
            .create(AudiobookshelfApi::class.java)
    }

    private companion object {
        const val ENDPOINT_TIMEOUT_MS = 5_000L
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}

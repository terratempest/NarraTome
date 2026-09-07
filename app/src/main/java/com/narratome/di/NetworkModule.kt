package com.narratome.di

import com.narratome.data.remote.AudiobookshelfApi
import com.narratome.BuildConfig
import com.narratome.data.remote.AuthInterceptor
import com.narratome.data.remote.FailoverInterceptor
import com.narratome.data.remote.StrictHttpsPolicy
import com.narratome.data.local.preferences.AppPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import com.narratome.data.download.ResumableDownloader
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        // Omit null fields from serialized request bodies — critical for PATCH semantics.
        // Sending "field": null can trigger silent no-ops on the Audiobookshelf server
        // due to JS loose-equality null/undefined checks.
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideStrictHttpsPolicy(
        preferences: AppPreferencesRepository,
        @ApplicationScope scope: CoroutineScope,
    ): StrictHttpsPolicy =
        StrictHttpsPolicy(runBlocking { preferences.requireHttps.first() }).also { policy ->
            scope.launch {
                preferences.requireHttps.distinctUntilChanged().collect { policy.update(it) }
            }
        }

    // User-managed servers retain normal platform TLS validation, without fixed certificate pins.
    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        failoverInterceptor: FailoverInterceptor,
        httpsPolicy: StrictHttpsPolicy,
    ): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
        
        // Failover rewrites each attempt before authentication.
        return OkHttpClient.Builder()
            .connectTimeout(45, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(45, TimeUnit.SECONDS)
            .addInterceptor(failoverInterceptor)
            .addInterceptor { chain ->
                httpsPolicy.check(chain.request().url)
                chain.proceed(chain.request())
            }
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .addNetworkInterceptor(httpsPolicy)
            .addNetworkInterceptor { chain ->
                authInterceptor.checkDestination(chain.request())
                chain.proceed(chain.request())
            }
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://127.0.0.1/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun provideApi(retrofit: Retrofit): AudiobookshelfApi =
        retrofit.create(AudiobookshelfApi::class.java)

    @Provides
    @Singleton
    fun provideResumableDownloader(client: OkHttpClient): ResumableDownloader =
        ResumableDownloader(client)
}

package com.narratome.data.remote

import com.narratome.data.download.ResumableDownloader
import com.narratome.data.repository.MediaConsent
import com.narratome.data.repository.MediaNetworkState
import com.narratome.domain.model.EndpointMode
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.narratome.data.repository.probeServer

class MediaNetworkTest {
    @Test fun reachabilityTriesBackupEvenWhilePrimaryDnsIsStalled() = runBlocking {
        MockWebServer().use { backup ->
            backup.start()
            val releaseDns = CountDownLatch(1)
            val resolver = ServerBaseUrlResolver {
                ServerEndpointPrefsSnapshot("https://primary.invalid", backup.url("/").toString(), EndpointMode.AUTO, "")
            }
            val client = OkHttpClient.Builder().dns { host ->
                if (host == "primary.invalid") {
                    releaseDns.await(5, TimeUnit.SECONDS)
                    throw java.net.UnknownHostException(host)
                }
                okhttp3.Dns.SYSTEM.lookup(host)
            }.addInterceptor(FailoverInterceptor(resolver)).build()
            val api = Retrofit.Builder().baseUrl("https://127.0.0.1/").client(client)
                .addConverterFactory(Json.asConverterFactory("application/json".toMediaType()))
                .build().create(AudiobookshelfApi::class.java)
            try {
                backup.enqueue(MockResponse().setBody("{\"success\":true}"))
                assertTrue(probeServer(api, resolver, timeoutMs = 500))
                assertEquals(backup.url("/"), resolver.selected())
            } finally { releaseDns.countDown() }
        }
    }

    @Test fun ignoredRangeRestartsWithoutAppendingAndCompleteRangeKeepsFile() {
        MockWebServer().use { server ->
            server.start()
            val file = File.createTempFile("media-range", ".bin")
            try {
                file.writeText("partial")
                val downloader = ResumableDownloader(OkHttpClient())
                server.enqueue(MockResponse().setBody("complete"))
                downloader.download(server.url("/audio").toString(), file, { false }, { _, _ -> }, true)
                assertEquals("bytes=7-", server.takeRequest().getHeader("Range"))
                assertEquals("complete", file.readText())
                server.enqueue(MockResponse().setResponseCode(416).setHeader("Content-Range", "bytes */8"))
                downloader.download(server.url("/audio").toString(), file, { false }, { _, _ -> }, true)
                assertEquals("complete", file.readText())
            } finally { file.delete() }
        }
    }

    @Test fun consentIsPerActionAndNetworkGeneration() {
        val consent = MediaConsent()
        val metered = MediaNetworkState(connected = true, metered = true, generation = 1)
        assertFalse(consent.allowed("play:one", metered))
        consent.allow("play:one", metered)
        assertTrue(consent.allowed("play:one", metered))
        assertFalse(consent.allowed("play:two", metered))
        assertFalse(consent.allowed("download:one", metered))
        assertFalse(consent.allowed("play:one", metered.copy(generation = 2)))
        assertFalse(consent.allowed("play:one", metered.copy(connected = false)))
        assertTrue(consent.allowed("download:any", metered.copy(metered = false)))
        assertTrue(consent.allowed("download:any", metered.copy(alwaysAllow = true)))
        consent.revoke("play:one")
        assertFalse(consent.allowed("play:one", metered))
    }

    @Test fun fallbackRetainsEndpointAndPreservesMediaPathQueryAndRange() {
        MockWebServer().use { primary -> MockWebServer().use { backup ->
            primary.start(); backup.start()
            var config = ServerEndpointPrefsSnapshot(primary.url("/abs/").toString(), backup.url("/books/").toString(), EndpointMode.AUTO, "")
            val resolver = ServerBaseUrlResolver { config }
            val client = OkHttpClient.Builder().addInterceptor(FailoverInterceptor(resolver)).build()
            primary.enqueue(MockResponse().setResponseCode(503))
            backup.enqueue(MockResponse().setBody("backup"))
            val request = Request.Builder().url(primary.url("/abs/audio/part.m4b?x=1"))
                .header("Range", "bytes=123-").header("Authorization", "Bearer secret").build()
            client.newCall(request).execute().use { assertEquals("backup", it.body.string()) }
            val received = backup.takeRequest()
            assertEquals("/books/audio/part.m4b?x=1", received.path)
            assertEquals("bytes=123-", received.getHeader("Range"))
            assertEquals("Bearer secret", received.getHeader("Authorization"))
            backup.enqueue(MockResponse().setBody("again"))
            client.newCall(request).execute().close()
            assertEquals(1, primary.requestCount)
            resolver.reset()
            assertEquals(primary.url("/abs/"), resolver.selected())
            config = config.copy(endpointMode = EndpointMode.PRIMARY)
            primary.enqueue(MockResponse().setResponseCode(503))
            client.newCall(request).execute().use { assertEquals(503, it.code) }
            assertEquals(2, backup.requestCount)
            config = config.copy(endpointMode = EndpointMode.SECONDARY)
            assertEquals(backup.url("/books/"), resolver.selected())
            val foreign = "https://unconfigured.example/audio?token=secret".toHttpUrl()
            assertNull(serverUrl(foreign, backup.url("/books/"), resolver.configured()))
            assertFalse(primary.url("/abs-other/audio").belongsTo(primary.url("/abs/")))
        } }
    }

    @Test fun unreachablePrimaryFallsBackAndBothFailuresSurface() {
        MockWebServer().use { primary -> MockWebServer().use { backup ->
            primary.start(); backup.start()
            val config = ServerEndpointPrefsSnapshot(primary.url("/").toString(), backup.url("/").toString(), EndpointMode.AUTO, "")
            primary.shutdown()
            val resolver = ServerBaseUrlResolver { config }
            val client = OkHttpClient.Builder().addInterceptor(FailoverInterceptor(resolver)).build()
            backup.enqueue(MockResponse().setBody("ok"))
            val request = Request.Builder().url("https://127.0.0.1/ping").build()
            client.newCall(request).execute().use { assertEquals("ok", it.body.string()) }
            backup.shutdown()
            assertThrows(IOException::class.java) { client.newCall(request).execute().close() }
        } }
    }

    @Test fun networkPauseKeepsPartialBytesEvenWithStrictCleanupAndResumesThroughBackup() {
        MockWebServer().use { primary -> MockWebServer().use { backup ->
            primary.start(); backup.start()
            val resolver = ServerBaseUrlResolver {
                ServerEndpointPrefsSnapshot(primary.url("/").toString(), backup.url("/").toString(), EndpointMode.AUTO, "")
            }
            val client = OkHttpClient.Builder().addInterceptor(FailoverInterceptor(resolver)).build()
            val downloader = ResumableDownloader(client)
            val file = File.createTempFile("media-resume", ".bin")
            try {
                val content = "a".repeat(200_000)
                val paused = AtomicBoolean(false)
                primary.enqueue(MockResponse().setBody(content))
                assertThrows(IOException::class.java) {
                    downloader.download(primary.url("/audio").toString(), file, paused::get, { _, _ -> paused.set(true) }, true)
                }
                val saved = file.length()
                assertTrue(saved in 1 until content.length.toLong())
                primary.enqueue(MockResponse().setResponseCode(503))
                backup.enqueue(MockResponse().setResponseCode(206)
                    .setHeader("Content-Range", "bytes $saved-${content.length - 1}/${content.length}")
                    .setBody(content.substring(saved.toInt())))
                downloader.download(primary.url("/audio").toString(), file, { false }, { _, _ -> }, true)
                assertEquals("bytes=$saved-", backup.takeRequest().getHeader("Range"))
                assertEquals(content, file.readText())
            } finally { file.delete() }
        } }
    }
}

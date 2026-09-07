package com.narratome.data.remote

import com.narratome.data.download.ResumableDownloader
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

class StrictHttpsPolicyTest {
    @Test fun httpsDowngradeRedirectIsRejectedBeforeCredentialsReachHttp() {
        // Generate a disposable localhost key with the JDK; no private key is versioned.
        val directory = java.nio.file.Files.createTempDirectory("narratome-tls").toFile()
        val store = java.security.KeyStore.getInstance("PKCS12")
        try {
            val keytool = File(System.getProperty("java.home"),
                "bin/" + if (File.separatorChar == '\\') "keytool.exe" else "keytool")
            val keystore = File(directory, "localhost.p12")
            val output = File(directory, "keytool.log")
            val process = ProcessBuilder(
                keytool.path, "-genkeypair", "-noprompt", "-alias", "localhost",
                "-keyalg", "RSA", "-keysize", "2048", "-validity", "2",
                "-dname", "CN=localhost", "-ext", "SAN=dns:localhost,ip:127.0.0.1",
                "-storetype", "PKCS12", "-keystore", keystore.path,
                "-storepass", "changeit", "-keypass", "changeit"
            ).redirectErrorStream(true).redirectOutput(output).start()
            try {
                check(process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)) { "Test key generation timed out" }
                check(process.exitValue() == 0) { "Test key generation failed: ${output.readText()}" }
            } finally {
                if (process.isAlive) process.destroyForcibly().waitFor()
            }
            keystore.inputStream().use { store.load(it, "changeit".toCharArray()) }
        } finally {
            check(directory.deleteRecursively()) { "Could not remove temporary test key" }
        }
        val keys = javax.net.ssl.KeyManagerFactory.getInstance(javax.net.ssl.KeyManagerFactory.getDefaultAlgorithm()).apply {
            init(store, "changeit".toCharArray())
        }
        val trust = javax.net.ssl.TrustManagerFactory.getInstance(javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm()).apply {
            init(store)
        }.trustManagers.filterIsInstance<javax.net.ssl.X509TrustManager>().single()
        val tls = javax.net.ssl.SSLContext.getInstance("TLS").apply { init(keys.keyManagers, arrayOf(trust), null) }
        MockWebServer().use { secure -> MockWebServer().use { cleartext ->
            secure.useHttps(tls.socketFactory, false)
            secure.start(); cleartext.start()
            val resolver = ServerBaseUrlResolver {
                ServerEndpointPrefsSnapshot(secure.url("/").toString(), cleartext.url("/").toString(), EndpointMode.PRIMARY, "")
            }
            val policy = StrictHttpsPolicy(true)
            val client = OkHttpClient.Builder().sslSocketFactory(tls.socketFactory, trust)
                .addInterceptor(AuthInterceptor({ "secret" }, resolver)).addNetworkInterceptor(policy).build()
            secure.enqueue(MockResponse().setResponseCode(302).setHeader("Location", cleartext.url("/audio")))
            assertThrows(HttpsRequiredException::class.java) {
                client.newCall(Request.Builder().url(secure.url("/audio")).build()).execute().close()
            }
            assertEquals("Bearer secret", secure.takeRequest().getHeader("Authorization"))
            assertEquals(0, cleartext.requestCount)
        } }
    }

    @Test fun httpDefaultsToAllowedAndStrictBlocksInitialAndFallbackRequests() {
        MockWebServer().use { server ->
            server.start()
            val policy = StrictHttpsPolicy()
            val resolver = ServerBaseUrlResolver {
                ServerEndpointPrefsSnapshot("https://primary.invalid", server.url("/").toString(), EndpointMode.AUTO, "")
            }
            val client = OkHttpClient.Builder()
                .dns { host -> if (host == "primary.invalid") throw java.net.UnknownHostException(host) else okhttp3.Dns.SYSTEM.lookup(host) }
                .addInterceptor(FailoverInterceptor(resolver))
                .addInterceptor { chain -> policy.check(chain.request().url); chain.proceed(chain.request()) }
                .addNetworkInterceptor(policy).build()
            server.enqueue(MockResponse().setBody("audio"))
            client.newCall(Request.Builder().url("https://127.0.0.1/audio").build()).execute().use {
                assertEquals("audio", it.body.string())
            }
            policy.update(true)
            val directClient = OkHttpClient.Builder().addNetworkInterceptor(policy).build()
            assertThrows(HttpsRequiredException::class.java) {
                directClient.newCall(Request.Builder().url(server.url("/audio")).build()).execute().close()
            }
            resolver.reset()
            assertThrows(HttpsRequiredException::class.java) {
                client.newCall(Request.Builder().url("https://127.0.0.1/audio").build()).execute().close()
            }
            policy.check("https://server.example/audio".toHttpUrl())
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun changingPolicyDuringDownloadPreservesPartialFileAndResumesWithRange() {
        MockWebServer().use { server ->
            server.start()
            val policy = StrictHttpsPolicy()
            val client = OkHttpClient.Builder().addNetworkInterceptor(policy).build()
            val file = File.createTempFile("https-resume", ".bin")
            val downloader = ResumableDownloader(client)
            try {
                val content = "a".repeat(200_000)
                server.enqueue(MockResponse().setBody(content))
                assertThrows(HttpsRequiredException::class.java) {
                    downloader.download(server.url("/audio").toString(), file, { false }, { _, _ -> policy.update(true) }, true)
                }
                val saved = file.length()
                assertTrue(saved in 1 until content.length.toLong())
                server.takeRequest()
                policy.update(false)
                server.enqueue(MockResponse().setResponseCode(206)
                    .setHeader("Content-Range", "bytes $saved-${content.length - 1}/${content.length}")
                    .setBody(content.substring(saved.toInt())))
                downloader.download(server.url("/audio").toString(), file, { false }, { _, _ -> }, true)
                assertEquals("bytes=$saved-", server.takeRequest().getHeader("Range"))
                assertEquals(content, file.readText())
            } finally { file.delete() }
        }
    }

    @Test fun bearerAuthenticationStaysOnConfiguredServerAndForeignTokenRedirectIsRejected() {
        MockWebServer().use { server -> MockWebServer().use { foreign ->
            server.start(); foreign.start()
            val resolver = ServerBaseUrlResolver {
                ServerEndpointPrefsSnapshot(server.url("/").toString(), "", EndpointMode.PRIMARY, "")
            }
            val auth = AuthInterceptor({ "session-secret" }, resolver)
            val client = OkHttpClient.Builder().addInterceptor(auth).addNetworkInterceptor { chain ->
                auth.checkDestination(chain.request()); chain.proceed(chain.request())
            }.build()
            server.enqueue(MockResponse().setBody("audio"))
            client.newCall(Request.Builder().url(server.url("/audio")).build()).execute().close()
            val media = server.takeRequest()
            assertEquals("Bearer session-secret", media.getHeader("Authorization"))
            assertNull(media.requestUrl!!.queryParameter("token"))
            foreign.enqueue(MockResponse().setBody("public audio"))
            client.newCall(Request.Builder().url(foreign.url("/audio"))
                .header("Authorization", "Bearer session-secret").build()).execute().close()
            assertNull(foreign.takeRequest().getHeader("Authorization"))
            server.enqueue(MockResponse().setResponseCode(302)
                .setHeader("Location", foreign.url("/audio?token=session-secret")))
            assertThrows(IOException::class.java) {
                client.newCall(Request.Builder().url(server.url("/redirect")).build()).execute().close()
            }
            assertEquals(1, foreign.requestCount)
        } }
    }
}

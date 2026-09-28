package com.narratome.data.repository

import com.narratome.data.remote.StrictHttpsPolicy
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class ServerSetupValidatorTest {
    @Test
    fun checksPrimaryAndBackupBeforeLoggingInToPrimary() = runBlocking {
        MockWebServer().use { primary -> MockWebServer().use { backup ->
            primary.start()
            backup.start()
            primary.enqueue(pingSuccess())
            primary.enqueue(MockResponse().setBody("""{"user":{"username":"alice","token":"session-token"}}"""))
            backup.enqueue(pingSuccess())

            val result = validator().validate(
                primary = primary.url("/primary/"),
                backup = backup.url("/backup/"),
                username = "alice",
                password = "example-password",
            )

            assertEquals("alice", result.username)
            assertEquals("session-token", result.token)
            assertEquals("GET", primary.takeRequest().method)
            val login = primary.takeRequest()
            assertEquals("POST", login.method)
            assertTrue(login.body.readUtf8().contains("example-password"))
            assertEquals("GET", backup.takeRequest().method)
        } }
    }

    @Test
    fun backupFailureStopsBeforeLogin() = runBlocking {
        MockWebServer().use { primary -> MockWebServer().use { backup ->
            primary.start()
            backup.start()
            primary.enqueue(pingSuccess())
            backup.enqueue(MockResponse().setResponseCode(503))

            val error = assertThrows(ServerSetupValidationException::class.java) {
                runBlocking {
                    validator().validate(
                        primary = primary.url("/"),
                        backup = backup.url("/"),
                        username = "alice",
                        password = "example-password",
                    )
                }
            }

            assertTrue(error.message.orEmpty().contains("Backup server could not be reached"))
            assertEquals(1, primary.requestCount)
            assertEquals(1, backup.requestCount)
        } }
    }

    @Test
    fun rejectedCredentialsHaveSafeActionableError() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(pingSuccess())
            server.enqueue(MockResponse().setResponseCode(401))

            val error = assertThrows(ServerSetupValidationException::class.java) {
                runBlocking {
                    validator().validate(
                        primary = server.url("/"),
                        backup = null,
                        username = "alice",
                        password = "example-password",
                    )
                }
            }

            assertEquals("Username or password was not accepted by the primary server.", error.message)
            assertEquals(2, server.requestCount)
        }
    }

    @Test
    fun requiredHttpsRejectsHttpBeforeConnecting() = runBlocking {
        MockWebServer().use { server ->
            server.start()

            val error = assertThrows(ServerSetupValidationException::class.java) {
                runBlocking {
                    validator().validate(
                        primary = server.url("/"),
                        backup = null,
                        username = "alice",
                        password = "example-password",
                        requireHttps = true,
                    )
                }
            }

            assertTrue(error.message.orEmpty().contains("Require HTTPS"))
            assertEquals(0, server.requestCount)
        }
    }

    @Test
    fun pingRedirectIsNotFollowed() = runBlocking {
        MockWebServer().use { server -> MockWebServer().use { redirectTarget ->
            server.start()
            redirectTarget.start()
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", redirectTarget.url("/ping")))

            assertThrows(ServerSetupValidationException::class.java) {
                runBlocking {
                    validator().validate(
                        primary = server.url("/"),
                        backup = null,
                        username = "alice",
                        password = "example-password",
                    )
                }
            }

            assertEquals(1, server.requestCount)
            assertEquals(0, redirectTarget.requestCount)
        } }
    }

    private fun validator() = ServerSetupValidator(Json { ignoreUnknownKeys = true }, StrictHttpsPolicy(false))

    private fun pingSuccess() = MockResponse()
        .setHeader("Content-Type", "application/json")
        .setBody("""{"success":true}""")
}

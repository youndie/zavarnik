package io.github.youndie.zavarnik.runner

import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** A real server on a free port: the workload's HTTP path is the thing under test, not a mock of it. */
class WorkloadTest {
    private val received = ArrayList<String>()
    private val server: HttpServer =
        HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/login") { exchange ->
                val body = """{"accessToken": "t-9", "user": {"id": 42}}""".toByteArray()
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            createContext("/home") { exchange ->
                received += "${exchange.requestURI} ${exchange.requestHeaders.getFirst("Authorization")}"
                val ok = exchange.requestHeaders.getFirst("Authorization") == "Bearer t-9"
                val body = (if (ok) "hi" else "no").toByteArray()
                exchange.sendResponseHeaders(if (ok) 200 else 401, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            start()
        }
    private val base = "http://127.0.0.1:${server.address.port}"
    private val workload = Workload(Files.createTempFile("workload", ".log").toFile())

    @AfterTest
    fun stop() {
        server.stop(0)
    }

    @Test
    fun `a captured value is sent by the next step, in the header and in the URL`() {
        workload.run(
            WorkloadStep.http(
                "POST",
                "$base/login",
                "application/json",
                "{}",
                captures =
                    mapOf("token" to "accessToken", "id" to "user.id"),
            ),
        )
        workload.run(
            WorkloadStep.http(
                "GET",
                "$base/home?user={{id}}",
                null,
                null,
                headers =
                    mapOf("Authorization" to "Bearer {{ token }}"),
            ),
        )
        assertEquals(listOf("/home?user=42 Bearer t-9"), received)
    }

    @Test
    fun `a placeholder nothing captured fails before the request is sent`() {
        val failure =
            assertFailsWith<RunnerException> {
                workload.run(
                    WorkloadStep.http(
                        "GET",
                        "$base/home",
                        null,
                        null,
                        headers =
                            mapOf("Authorization" to "Bearer {{token}}"),
                    ),
                )
            }
        assertContains(failure.message!!, "{{token}}, which no earlier step captured")
        assertEquals(emptyList(), received)
    }

    @Test
    fun `a non-2xx answer names the step and the status`() {
        val failure =
            assertFailsWith<RunnerException> { workload.run(WorkloadStep.http("GET", "$base/home", null, null)) }
        assertContains(failure.message!!, "GET $base/home answered 401")
    }

    @Test
    fun `a capture from a non-JSON answer says so`() {
        workload.run(
            WorkloadStep.http(
                "POST",
                "$base/login",
                "application/json",
                "{}",
                captures =
                    mapOf("token" to "accessToken"),
            ),
        )
        val failure =
            assertFailsWith<RunnerException> {
                workload.run(
                    WorkloadStep.http(
                        "GET",
                        "$base/home",
                        null,
                        null,
                        headers =
                            mapOf("Authorization" to "Bearer {{token}}"),
                        captures = mapOf("x" to "y"),
                    ),
                )
            }
        assertContains(failure.message!!, "did not answer JSON")
    }

    @Test
    fun `command words are expanded too`() {
        workload.run(
            WorkloadStep.http(
                "POST",
                "$base/login",
                "application/json",
                "{}",
                captures =
                    mapOf("id" to "user.id"),
            ),
        )
        val out = File(Files.createTempDirectory("wl").toFile(), "out.txt")
        workload.run(WorkloadStep.command(listOf("sh", "-c", "echo user={{id}} > ${out.absolutePath}")))
        assertEquals("user=42", out.readText().trim())
    }
}

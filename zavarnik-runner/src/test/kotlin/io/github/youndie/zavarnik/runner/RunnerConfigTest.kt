package io.github.youndie.zavarnik.runner

import java.io.File
import java.nio.file.Files
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RunnerConfigTest {
    private val dir: File = Files.createTempDirectory("zavarnik-config").toFile()

    @Test
    fun `survives the round trip through the properties file, workload order and bodies included`() {
        val config =
            RunnerConfig(
                cacheFileName = "svc.aot",
                readyUrl = "http://localhost:18090/health",
                exitAfter = null,
                readyTimeout = Duration.ofSeconds(90),
                shutdownTimeout = Duration.ofMinutes(3),
                workload =
                    listOf(
                        WorkloadStep.http("GET", "http://localhost:18090/api/items", null, null),
                        WorkloadStep.http(
                            "POST",
                            "http://localhost:18090/api/items",
                            "application/json",
                            "{\"name\": \"a=b, c\\nd\"}",
                        ),
                        WorkloadStep.command(listOf("sh", "-c", "echo 'x y'; exit 0")),
                        WorkloadStep.http(
                            "POST",
                            "http://localhost:18090/auth/login",
                            "application/json",
                            "{}",
                            headers = mapOf("X-Trace" to "t=1"),
                            captures = mapOf("token" to "accessToken", "id" to "user.id"),
                        ),
                        WorkloadStep.http(
                            "GET",
                            "http://localhost:18090/home?u={{id}}",
                            null,
                            null,
                            headers = mapOf("Authorization" to "Bearer {{token}}", "Accept" to "application/json"),
                        ),
                    ),
                minCachedShare = 0.85,
                verifyJvmArgs = listOf("-javaagent:agent.jar", "--add-modules=jdk.httpserver"),
            )
        val file = File(dir, RunnerConfig.FILE_NAME)
        config.write(file)
        assertEquals(config, RunnerConfig.read(file))
    }

    @Test
    fun `a timer-driven configuration with no workload reads back as written`() {
        val config =
            RunnerConfig(
                cacheFileName = "app.aot",
                readyUrl = null,
                exitAfter = Duration.ofSeconds(3),
                readyTimeout = Duration.ofMinutes(2),
                shutdownTimeout = Duration.ofMinutes(5),
                workload = emptyList(),
                minCachedShare = 0.9,
                verifyJvmArgs = emptyList(),
            )
        val file = File(dir, RunnerConfig.FILE_NAME)
        config.write(file)
        assertEquals(config, RunnerConfig.read(file))
    }

    @Test
    fun `a distribution without the file says so instead of guessing`() {
        val failure = assertFailsWith<RunnerException> { RunnerConfig.read(File(dir, RunnerConfig.FILE_NAME)) }
        assertEquals(true, failure.message?.contains("built with the plugin"))
    }
}

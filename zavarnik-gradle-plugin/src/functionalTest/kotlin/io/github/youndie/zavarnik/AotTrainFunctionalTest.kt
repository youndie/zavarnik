package io.github.youndie.zavarnik

import io.github.youndie.zavarnik.runner.Training
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AotTrainFunctionalTest {
    private val projectDir: File = Files.createTempDirectory("zavarnik-train").toFile()

    @Test
    fun `trains a cache through the start script, normalises jar mtimes and writes the manifest`() {
        Fixture.write(
            projectDir,
            extension =
                """
                zavarnik {
                    training {
                        readyWhen.url("http://127.0.0.1:${Fixture.PORT}/health")
                        workload { exec("curl", "-sf", "http://127.0.0.1:${Fixture.PORT}/work") }
                    }
                }
                """.trimIndent(),
        )
        val result = runner("aotTrain").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":aotTrain")?.outcome)
        assertContains(result.output, "application ready after")
        val lib = File(projectDir, "build/install/fixture/lib")
        val cache = File(lib, "app.aot")
        assertTrue(cache.length() > 1_000_000, "cache is ${cache.length()} bytes")
        val manifest = File(lib, "app.aot.jars").readLines().filter { it.isNotBlank() }
        assertEquals(listOf("fixture.jar", "zavarnik-runner.jar"), manifest.map { it.substringAfter("  ") })
        assertEquals(Training.JAR_MTIME.toMillis(), File(lib, "fixture.jar").lastModified())
        val log = File(projectDir, "build/zavarnik/aotTrain.log").readText()
        assertContains(log, "AOTCache creation is complete")

        // A second run must not trip over the cache it made ("Only one of AOTCache or AOTCacheOutput").
        val again = runner("aotTrain", "--rerun-tasks").build()
        assertEquals(TaskOutcome.SUCCESS, again.task(":aotTrain")?.outcome)
    }

    @Test
    fun `exitAfter trains an application without a readiness URL`() {
        Fixture.write(projectDir, extension = "zavarnik { training { exitAfter = Duration.ofSeconds(3) } }")
        val result = runner("aotTrain").build()
        assertEquals(TaskOutcome.SUCCESS, result.task(":aotTrain")?.outcome)
        assertTrue(File(projectDir, "build/install/fixture/lib/app.aot").isFile)
    }

    @Test
    fun `fails, without a cache, when the application never becomes ready`() {
        Fixture.write(
            projectDir,
            extension =
                """
                zavarnik {
                    training {
                        readyWhen.url("http://127.0.0.1:${Fixture.PORT + 1}/health")
                        readyTimeout = Duration.ofSeconds(5)
                    }
                }
                """.trimIndent(),
        )
        val result = runner("aotTrain").buildAndFail()
        assertContains(result.output, "did not answer 200 within 5 s")
        assertTrue(!File(projectDir, "build/install/fixture/lib/app.aot").exists())
    }

    @Test
    fun `refuses a training block with neither readiness nor exitAfter`() {
        Fixture.write(projectDir)
        val result = runner("aotTrain").buildAndFail()
        assertContains(result.output, "needs a way to know when to stop")
    }

    private fun runner(vararg args: String): GradleRunner =
        GradleRunner
            .create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments(*args, "--stacktrace")
}

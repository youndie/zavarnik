package io.github.youndie.zavarnik

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Jib mode against a real Jib build and a real Docker daemon: `jibAotTrain` trains inside the
 * image, `jibAotVerify` — in a second invocation, as documented — verifies inside the image built
 * with the cache. Skipped where there is no Docker.
 */
class JibFunctionalTest {
    private val projectDir: File = Files.createTempDirectory("zavarnik-jib").toFile()
    private val image = "zavarnik-jib-fixture-${System.nanoTime()}"

    private fun jib(mode: String) =
        """
        jib {
            from { image = "eclipse-temurin:25-jre" }
            to { image = "$image" }
            containerizingMode = "$mode"
        }
        zavarnik {
            jvmArgs("-Dfixture.marker=1")
            training {
                readyWhen.url("http://127.0.0.1:${Fixture.PORT}/health")
                workload { get("http://127.0.0.1:${Fixture.PORT}/work") }
            }
        }
        """.trimIndent()

    @Test
    fun `the exploded layout is refused at configuration time`() {
        Fixture.write(
            projectDir,
            extension = jib("exploded"),
            plugins = "id(\"com.google.cloud.tools.jib\") version \"3.5.4\"",
        )
        val result = runner("jibAotTrain").buildAndFail()
        assertContains(result.output, "containerizingMode = \"packaged\"")
    }

    @Test
    fun `trains inside the Jib image and verifies inside the image built with the cache`() {
        assumeTrue(dockerAvailable(), "no Docker daemon here")
        Fixture.write(
            projectDir,
            extension = jib("packaged"),
            plugins = "id(\"com.google.cloud.tools.jib\") version \"3.5.4\"",
        )
        try {
            // Nothing trained yet: the image this invocation builds carries no cache, and verify says so.
            val tooEarly = runner("jibAotVerify").buildAndFail()
            assertContains(tooEarly.output, "carries no app.aot")

            val train = runner("jibAotTrain").build()
            assertEquals(TaskOutcome.SUCCESS, train.task(":jibAotTrain")?.outcome)
            assertContains(train.output, "trained inside $image:latest")
            assertTrue(File(projectDir, "build/zavarnik/jib/app.aot").length() > 1_000_000)
            assertTrue(File(projectDir, "build/zavarnik/jib/app.aot.jars").readText().contains("classpath/fixture.jar"))

            val verify = runner("jibAotVerify").build()
            assertEquals(TaskOutcome.SUCCESS, verify.task(":jibAotVerify")?.outcome)
            assertContains(verify.output, "application classes (100.0%) came from app.aot")
            val entrypoint = docker("inspect", "$image:latest", "--format", "{{json .Config.Entrypoint}}")
            assertContains(entrypoint, "-XX:AOTCache=/app/zavarnik/app.aot")
            assertContains(entrypoint, "-Dfixture.marker=1")
            assertContains(entrypoint, "-XX:-AOTAdapterCaching")
        } finally {
            docker("rmi", "-f", "$image:latest")
        }
    }

    private fun dockerAvailable(): Boolean =
        try {
            ProcessBuilder("docker", "info").redirectErrorStream(true).start().let { p ->
                p.inputStream.readAllBytes()
                p.waitFor() == 0
            }
        } catch (notFound: java.io.IOException) {
            false
        }

    private fun docker(vararg args: String): String {
        val process = ProcessBuilder("docker", *args).redirectErrorStream(true).start()
        val out = process.inputStream.bufferedReader().readText()
        process.waitFor()
        return out
    }

    private fun runner(vararg args: String): GradleRunner =
        GradleRunner
            .create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments(*args, "--stacktrace")
}

package io.github.youndie.zavarnik

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * `training { environment(…) }` — the training stand's environment on this machine.
 *
 * The application under test refuses to start when `FIXTURE_STORE` is absent, the way an
 * application on a typed configuration schema does, and prints the value it got when it is there.
 * Without the block that refusal is what the training run waits for a readiness URL behind.
 */
class TrainingEnvironmentFunctionalTest {
    private val projectDir: File = Files.createTempDirectory("zavarnik-environment").toFile()

    @Test
    fun `the value reaches the training run and the verification run`() {
        val store = File(projectDir, "build/tmp/aot-train/store.db").absolutePath
        Fixture.write(
            projectDir,
            extension = extension("environment(\"$VARIABLE\", \"$store\")"),
            requiredEnv = VARIABLE,
        )

        val result = runner("aotVerify").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":aotTrain")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":aotVerify")?.outcome)
        assertContains(File(projectDir, "build/zavarnik/aotTrain.log").readText(), "fixture: $VARIABLE=$store")
        assertContains(File(projectDir, "build/zavarnik/aotVerify.log").readText(), "fixture: $VARIABLE=$store")
        // Not in the distribution: the path is this machine's, and the same file travels into an
        // image, where a build directory does not exist.
        assertFalse(
            VARIABLE in File(projectDir, "build/install/fixture/lib/zavarnik.properties").readText(),
        )
    }

    @Test
    fun `without it the application refuses and the training run says so`() {
        Fixture.write(projectDir, extension = extension(""), requiredEnv = VARIABLE)

        val result = runner("aotTrain").buildAndFail()

        assertContains(result.output, "the application exited before")
        assertContains(result.output, "fixture: $VARIABLE is not set")
        assertFalse(File(projectDir, "build/install/fixture/lib/app.aot").exists())
    }

    @Test
    fun `a changed value trains again`() {
        Fixture.write(
            projectDir,
            extension = extension("environment(\"$VARIABLE\", \"first\")"),
            requiredEnv = VARIABLE,
        )
        assertEquals(TaskOutcome.SUCCESS, runner("aotTrain").build().task(":aotTrain")?.outcome)

        Fixture.write(
            projectDir,
            extension = extension("environment(\"$VARIABLE\", \"second\")"),
            requiredEnv = VARIABLE,
        )
        val again = runner("aotTrain").build()

        assertEquals(TaskOutcome.SUCCESS, again.task(":aotTrain")?.outcome)
        assertContains(File(projectDir, "build/zavarnik/aotTrain.log").readText(), "fixture: $VARIABLE=second")
    }

    private fun extension(environment: String): String =
        """
        zavarnik {
            training {
                $environment
                readyWhen.url("http://127.0.0.1:${Fixture.PORT}/health")
                workload { get("http://127.0.0.1:${Fixture.PORT}/work") }
            }
        }
        """.trimIndent()

    private fun runner(vararg args: String): GradleRunner =
        GradleRunner
            .create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments(*args, "--stacktrace")

    private companion object {
        const val VARIABLE = "FIXTURE_STORE"
    }
}

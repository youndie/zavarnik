package io.github.youndie.zavarnik

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AotReportFunctionalTest {
    private val projectDir: File = Files.createTempDirectory("zavarnik-report").toFile()

    @Test
    fun `writes sorted rows and medians for both variants`() {
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
        val result = runner("aotReport", "-Pzavarnik.runs=2").build()
        assertEquals(TaskOutcome.SUCCESS, result.task(":aotReport")?.outcome)
        assertContains(result.output, "readiness median")
        val report = File(projectDir, "build/reports/zavarnik/aotReport.md").readText()
        assertContains(report, "2 runs per variant")
        val rows = report.lines().filter { it.startsWith("| with") }
        assertEquals(2, rows.size, report)
        for (row in rows) {
            val runs =
                row
                    .split("|")[3]
                    .trim()
                    .split(" ")
                    .map(String::toLong)
            assertEquals(2, runs.size, row)
            assertTrue(runs == runs.sorted(), row)
        }
    }

    private fun runner(vararg args: String): GradleRunner =
        GradleRunner
            .create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments(*args, "--stacktrace")
}

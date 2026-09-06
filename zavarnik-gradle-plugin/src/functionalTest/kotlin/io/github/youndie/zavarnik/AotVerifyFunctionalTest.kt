package io.github.youndie.zavarnik

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class AotVerifyFunctionalTest {
    private val projectDir: File = Files.createTempDirectory("zavarnik-verify").toFile()

    private val trained =
        """
        zavarnik {
            training {
                readyWhen.url("http://127.0.0.1:${Fixture.PORT}/health")
                workload { exec("curl", "-sf", "http://127.0.0.1:${Fixture.PORT}/work") }
            }
        }
        """.trimIndent()

    @Test
    fun `verify passes after train, reports the cached share, and runs on check`() {
        Fixture.write(projectDir, extension = trained)
        val result = runner("check").build()
        assertEquals(TaskOutcome.SUCCESS, result.task(":aotTrain")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":aotVerify")?.outcome)
        assertContains(result.output, "application classes")
        assertContains(result.output, "came from app.aot")
        val report = File(projectDir, "build/zavarnik/aotVerify.txt").readText()
        assertContains(report, "of 1 application classes (100.0%)")
    }

    @Test
    fun `a jar changed after training fails verify by name, before the application is started`() {
        Fixture.write(projectDir, extension = trained)
        runner("aotTrain").build()
        val jar = File(projectDir, "build/install/fixture/lib/fixture.jar")
        addEntry(jar, "extra.txt", "rebuilt")
        // installDist is a Sync: it would notice its output changed, put the original jar back and
        // wipe the cache with it — which is the right behaviour, and not the one under test here.
        val result = runner("aotVerify", "-x", "aotTrain", "-x", "installDist").buildAndFail()
        assertContains(result.output, "fixture.jar: changed since aotTrain")
        assertEquals(false, File(projectDir, "build/zavarnik/aotVerify.log").exists())
    }

    @Test
    fun `verify without a cache says to run aotTrain`() {
        Fixture.write(projectDir, extension = trained)
        runner("installDist").build()
        val result = runner("aotVerify", "-x", "aotTrain").buildAndFail()
        assertContains(result.output, "run aotTrain first")
    }

    private fun addEntry(
        jar: File,
        name: String,
        content: String,
    ) {
        val rebuilt = File(jar.parentFile, "${jar.name}.tmp")
        ZipFile(jar).use { source ->
            ZipOutputStream(rebuilt.outputStream()).use { out ->
                for (entry in source.entries()) {
                    out.putNextEntry(ZipEntry(entry.name))
                    source.getInputStream(entry).use { it.copyTo(out) }
                    out.closeEntry()
                }
                out.putNextEntry(ZipEntry(name))
                out.write(content.toByteArray())
                out.closeEntry()
            }
        }
        rebuilt.copyTo(jar, overwrite = true)
        rebuilt.delete()
    }

    private fun runner(vararg args: String): GradleRunner =
        GradleRunner
            .create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments(*args, "--stacktrace")
}

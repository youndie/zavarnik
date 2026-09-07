package io.github.youndie.zavarnik

import io.github.youndie.zavarnik.runner.JarManifest
import io.github.youndie.zavarnik.runner.Training
import org.gradle.testkit.runner.GradleRunner
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DistributionFunctionalTest {
    private val projectDir: File = Files.createTempDirectory("zavarnik-dist").toFile()

    private val extension =
        """
        zavarnik {
            jvmArgs("-Xmx256m")
            training { exitAfter = Duration.ofSeconds(3) }
        }
        """.trimIndent()

    @Test
    fun `the start scripts carry the guard and the JVM arguments`() {
        Fixture.write(projectDir, extension = extension)
        runner("installDist").build()
        val unix = File(projectDir, "build/install/fixture/bin/fixture").readText()
        assertContains(unix, "if [ -f \"\$APP_HOME/lib/app.aot\" ]")
        assertContains(unix, "-Xmx256m")
        assertContains(unix, "-XX:-AOTAdapterCaching")
        val windows = File(projectDir, "build/install/fixture/bin/fixture.bat").readText()
        assertContains(windows, "if exist \"%APP_HOME%\\lib\\app.aot\"")
    }

    @Test
    fun `distTar ships the cache with jar mtimes the cache was trained against`() {
        Fixture.write(projectDir, extension = extension)
        runner("distTar").build()
        val tar = File(projectDir, "build/distributions/fixture.tar")
        val unpacked = Files.createTempDirectory("zavarnik-untar").toFile()
        val exit =
            ProcessBuilder("tar", "xf", tar.absolutePath)
                .directory(unpacked)
                .inheritIO()
                .start()
                .waitFor()
        assertEquals(0, exit)
        val lib = File(unpacked, "fixture/lib")
        assertTrue(File(lib, "app.aot").length() > 1_000_000)
        assertTrue(File(lib, "app.aot.jars").isFile)
        assertTrue(File(lib, "zavarnik.properties").isFile)
        assertTrue(File(lib, "zavarnik-runner.jar").length() > 1_000_000)
        assertEquals(Training.JAR_MTIME.toMillis(), File(lib, "fixture.jar").lastModified())
        assertTrue(JarManifest.differences(lib, File(lib, "app.aot.jars")).isEmpty())
    }

    @Test
    fun `distZip ships no cache and says why`() {
        Fixture.write(projectDir, extension = extension)
        val result = runner("distZip").build()
        assertContains(result.output, "distZip ships no AOT cache")
        ZipFile(File(projectDir, "build/distributions/fixture.zip")).use { zip ->
            assertFalse(zip.entries().asSequence().any { it.name.endsWith("app.aot") })
        }
    }

    private fun runner(vararg args: String): GradleRunner =
        GradleRunner
            .create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments(*args, "--stacktrace")
}

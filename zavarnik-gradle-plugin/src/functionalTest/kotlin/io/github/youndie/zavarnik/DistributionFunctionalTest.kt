package io.github.youndie.zavarnik

import io.github.youndie.zavarnik.runner.JarManifest
import io.github.youndie.zavarnik.runner.RunnerConfig
import io.github.youndie.zavarnik.runner.Training
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
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
    fun `the ports a checkpoint may leave open reach the runner's configuration file`() {
        Fixture.write(
            projectDir,
            extension =
                """
                zavarnik {
                    training { exitAfter = Duration.ofSeconds(3) }
                    crac {
                        ignoreRemotePort(9092)
                        ignoreRemotePort(5432)
                    }
                }
                """.trimIndent(),
        )
        runner("installDist").build()
        val config = RunnerConfig.read(File(projectDir, "build/install/fixture/lib/zavarnik.properties"))
        // Sorted rather than in the order they were declared: the set is unordered and this is a
        // task input, so a wandering order would rewrite the file for nothing.
        assertEquals(listOf(5432, 9092), config.cracIgnoredRemotePorts)
        assertEquals(RunnerConfig.DEFAULT_CRAC_IMAGE_DIR, config.cracImageDirName)
    }

    @Test
    fun `an application that talks to nothing declares no ports`() {
        Fixture.write(projectDir, extension = extension)
        runner("installDist").build()
        val config = RunnerConfig.read(File(projectDir, "build/install/fixture/lib/zavarnik.properties"))
        assertEquals(emptyList(), config.cracIgnoredRemotePorts)
    }

    @Test
    fun `installDist alone pins the jar mtimes, before any training`() {
        Fixture.write(projectDir, extension = extension)
        runner("installDist").build()
        val jar = File(projectDir, "build/install/fixture/lib/fixture.jar")
        assertEquals(Training.JAR_MTIME.toMillis(), jar.lastModified())
        assertTrue(!File(projectDir, "build/install/fixture/lib/app.aot").exists())
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
        assertTrue(JarManifest.differences(File(unpacked, "fixture"), listOf(lib), File(lib, "app.aot.jars")).isEmpty())
    }

    @Test
    fun `a wildcard classpath in the start script is refused before anything is trained`() {
        Fixture.write(
            projectDir,
            extension =
                extension +
                    """

                    tasks.named<org.gradle.jvm.application.tasks.CreateStartScripts>("startScripts") {
                        classpath = files("lib/*")
                    }
                    """.trimIndent(),
        )
        val result = runner("installDist").buildAndFail()
        assertContains(result.output, "CLASSPATH has a wildcard")
        assertContains(result.output, "differs between filesystems and container runtimes")
    }

    @Test
    fun `with onAssemble off, assemble does not train, and distTar still ships a cache that is there`() {
        Fixture.write(
            projectDir,
            extension =
                """
                zavarnik {
                    training {
                        exitAfter = Duration.ofSeconds(3)
                        onAssemble = false
                    }
                }
                """.trimIndent(),
        )
        val assemble = runner("assemble").build()
        assertEquals(null, assemble.task(":aotTrain"))
        assertTrue(!File(projectDir, "build/install/fixture/lib/app.aot").exists())
        ZipFile(File(projectDir, "build/distributions/fixture.zip")).use { zip ->
            assertFalse(zip.entries().asSequence().any { it.name.endsWith("app.aot") })
        }

        runner("aotTrain", "distTar").build()
        val listing =
            ProcessBuilder("tar", "tf", File(projectDir, "build/distributions/fixture.tar").absolutePath)
                .redirectErrorStream(true)
                .start()
                .inputStream
                .bufferedReader()
                .readText()
        assertContains(listing, "fixture/lib/app.aot")
        assertContains(listing, "fixture/lib/app.aot.jars")
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

    // With the configuration cache on: a consumer that has it on (konekt) found the zip warning
    // carrying the project into the cache, which the sample without it never saw.
    private fun runner(vararg args: String): GradleRunner =
        GradleRunner
            .create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments(*args, "--stacktrace", "--configuration-cache")
}

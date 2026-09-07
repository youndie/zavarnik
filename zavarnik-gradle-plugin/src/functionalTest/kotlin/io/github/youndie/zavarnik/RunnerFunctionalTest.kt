package io.github.youndie.zavarnik

import org.gradle.testkit.runner.GradleRunner
import java.io.File
import java.nio.file.Files
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The runner on its own: `installDist` (no training), then `train` and `verify` through
 * `java -cp lib/zavarnik-runner.jar`, the way a container's runtime stage runs them — no Gradle,
 * no plugin classpath, only the JDK. The JDK is the one these tests run on, which is the toolchain.
 */
class RunnerFunctionalTest {
    private val projectDir: File = Files.createTempDirectory("zavarnik-runner").toFile()
    private val install = File(projectDir, "build/install/fixture")
    private val lib = File(install, "lib")

    @Test
    fun `train and verify run from the distribution's own lib, and verify refuses a tampered jar`() {
        Fixture.write(
            projectDir,
            extension =
                """
                zavarnik {
                    training {
                        readyWhen.url("http://127.0.0.1:18765/health")
                        workload { get("http://127.0.0.1:18765/work") }
                    }
                }
                """.trimIndent(),
        )
        GradleRunner
            .create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("installDist", "--stacktrace")
            .build()
        assertTrue(File(lib, "zavarnik.properties").isFile)
        assertTrue(!File(lib, "app.aot").exists(), "installDist alone must not train")

        val train = runner("train")
        assertEquals(0, train.exit, train.output)
        assertContains(train.output, "cache app.aot is")
        assertTrue(File(lib, "app.aot").length() > 1_000_000)
        assertTrue(File(lib, "app.aot.jars").isFile)

        val verify = runner("verify")
        assertEquals(0, verify.exit, verify.output)
        assertContains(verify.output, "application classes (100.0%) came from app.aot")

        // A jar rebuilt after the training: same name, different bytes.
        val jar = File(lib, "fixture.jar")
        JarOutputStream(jar.outputStream()).use { out ->
            out.putNextEntry(ZipEntry("tampered.txt"))
            out.write(1)
            out.closeEntry()
        }
        val tampered = runner("verify")
        assertEquals(1, tampered.exit, tampered.output)
        assertContains(tampered.output, "the jars in lib/ are not the ones app.aot was trained against")
        assertContains(tampered.output, "fixture.jar")
    }

    private class Run(
        val exit: Int,
        val output: String,
    )

    private fun runner(command: String): Run {
        val java = File(System.getProperty("java.home"), "bin/java")
        val process =
            ProcessBuilder(
                java.absolutePath,
                "-cp",
                File(lib, RunnerFilesTask.RUNNER_JAR_NAME).absolutePath,
                "io.github.youndie.zavarnik.runner.Main",
                command,
            ).redirectErrorStream(true)
                .start()
        val output = process.inputStream.bufferedReader().readText()
        return Run(process.waitFor(), output)
    }
}

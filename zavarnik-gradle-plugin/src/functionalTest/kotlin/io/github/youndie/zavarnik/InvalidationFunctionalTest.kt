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

/**
 * The invalidation cases from `experiments/aot-validation/results/`, each as the plugin sees it.
 * The letters are the experiment's: R3 touched jar, R8 module options, R9 agent, E4 directory.
 */
class InvalidationFunctionalTest {
    private val projectDir: File = Files.createTempDirectory("zavarnik-invalidation").toFile()

    private fun training(extra: String = "") =
        """
        zavarnik {
            training {
                readyWhen.url("http://127.0.0.1:${Fixture.PORT}/health")
                workload { exec("curl", "-sf", "http://127.0.0.1:${Fixture.PORT}/work") }
            }
            $extra
        }
        """.trimIndent()

    @Test
    fun `R3 a touched jar is rejected by the JVM where it validates, and passes where it does not`() {
        Fixture.write(projectDir, extension = training())
        runner("aotTrain").build()
        val jar = File(projectDir, "build/install/fixture/lib/fixture.jar")
        // Any mtime other than the one aotTrain pinned; a fixed one, because the point is the
        // difference, not the clock.
        assertTrue(jar.setLastModified(Training.JAR_MTIME.toMillis() + TOUCH_OFFSET_MILLIS))
        val jdk = JdkVersion.parse(System.getProperty("java.runtime.version"))!!
        if (jdk.skipsJarValidation) {
            // JDK-8377932: the JVM does not look, the hash has not changed, so nothing objects. This
            // is the gap the manifest cannot close and the toolchain warning is for.
            runner("aotVerify", "-x", "aotTrain", "-x", "installDist").build()
        } else {
            val result = runner("aotVerify", "-x", "aotTrain", "-x", "installDist").buildAndFail()
            assertContains(result.output, "did not start with the cache under -XX:AOTMode=on")
            assertContains(result.output, "timestamp has changed")
        }
    }

    @Test
    fun `R8 a module option production adds is a mismatch aotVerify reports`() {
        Fixture.write(projectDir, extension = training("verify { jvmArgs(\"--add-modules\", \"jdk.httpserver\") }"))
        val result = runner("aotVerify").buildAndFail()
        assertContains(result.output, "Mismatched values for property jdk.module.addmods")
    }

    @Test
    fun `R9 an agent on one side only is rejected, on both sides it is fine`() {
        val agent = Fixture.agentJar(projectDir)
        Fixture.write(projectDir, extension = training("verify { jvmArgs(\"-javaagent:${agent.absolutePath}\") }"))
        val oneSided = runner("aotVerify").buildAndFail()
        assertContains(oneSided.output, "java.instrument")

        Fixture.write(projectDir, extension = training("jvmArgs(\"-javaagent:${agent.absolutePath}\")"))
        val bothSides = runner("aotVerify").build()
        assertEquals(TaskOutcome.SUCCESS, bothSides.task(":aotVerify")?.outcome)
    }

    /**
     * The experiment's E4 — a non-empty directory on the classpath, for which the JVM writes no
     * cache — cannot happen through the `application` plugin: `installDist` copies a directory
     * dependency's *files* flat into `lib/` and leaves `$APP_HOME/lib/conf` in the CLASSPATH line,
     * a path that does not exist. The JVM tolerates an absent entry as long as it is absent at
     * run time too, so the cache trains. What the application cannot do is read `conf/` from the
     * classpath — that is Gradle's layout, not the plugin's business, but it is worth knowing.
     */
    @Test
    fun `E4 a directory dependency dangles in the classpath rather than sitting on it, and the cache trains`() {
        val conf = File(projectDir, "conf")
        conf.mkdirs()
        File(conf, "app.properties").writeText("a=b\n")
        Fixture.write(
            projectDir,
            extension = training(),
            dependencies = "dependencies { runtimeOnly(files(\"conf\")) }",
        )
        val result = runner("aotTrain").build()
        assertEquals(TaskOutcome.SUCCESS, result.task(":aotTrain")?.outcome)
        assertTrue(File(projectDir, "build/install/fixture/lib/app.properties").isFile)
        assertTrue(!File(projectDir, "build/install/fixture/lib/conf").exists())
        assertContains(File(projectDir, "build/install/fixture/bin/fixture").readText(), "\$APP_HOME/lib/conf")
    }

    private companion object {
        const val TOUCH_OFFSET_MILLIS = 60_000L
    }

    private fun runner(vararg args: String): GradleRunner =
        GradleRunner
            .create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments(*args, "--stacktrace")
}

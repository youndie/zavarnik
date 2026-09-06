package io.github.youndie.zavarnik

import org.gradle.testkit.runner.GradleRunner
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

/**
 * What the plugin refuses before anything runs, checked on real builds. The JDKs are the ones
 * installed on the machine — a toolchain the build cannot find fails on provisioning, which is
 * not the failure under test, so every case names the message it expects.
 */
class ConfigurationChecksFunctionalTest {
    private val projectDir: File = Files.createTempDirectory("zavarnik-checks").toFile()

    @Test
    fun `refuses a JDK 21 toolchain naming JDK 25`() {
        write(toolchain = 21)
        val result = runner("help").buildAndFail()
        assertContains(result.output, "zavarnik needs a JDK 25 or newer toolchain")
    }

    @Test
    fun `warns about ZGC on a JDK 25 toolchain naming JEP 516, and goes on`() {
        write(toolchain = 25, extension = "zavarnik { jvmArgs(\"-XX:+UseZGC\") }")
        val result = runner("help").build()
        assertContains(result.output, "without archived heap objects")
        assertContains(result.output, "JEP 516")
    }

    @Test
    fun `refuses a project without the application plugin`() {
        write(toolchain = 25, application = false)
        val result = runner("help").buildAndFail()
        assertContains(result.output, "needs the `application` plugin")
    }

    @Test
    fun `accepts a JDK 25 application and warns about JDK-8377932 only where the JDK carries it`() {
        write(toolchain = 25)
        val result = runner("help").build()
        val jdk = JdkVersion.parse(System.getProperty("java.runtime.version"))
        if (jdk != null && jdk.feature == 25 && jdk.skipsJarValidation) {
            assertContains(result.output, "JDK-8377932")
        } else {
            assertFalse("JDK-8377932" in result.output, result.output)
        }
    }

    private fun runner(vararg args: String): GradleRunner =
        GradleRunner
            .create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments(*args, "--stacktrace")

    private fun write(
        toolchain: Int,
        application: Boolean = true,
        extension: String = "",
    ) {
        File(projectDir, "settings.gradle.kts").writeText("rootProject.name = \"checks\"\n")
        File(projectDir, "build.gradle.kts").writeText(
            """
            plugins {
                ${if (application) "application" else "java"}
                id("io.github.youndie.zavarnik")
            }
            java { toolchain { languageVersion = JavaLanguageVersion.of($toolchain) } }
            ${if (application) "application { mainClass = \"demo.App\" }" else ""}
            $extension
            """.trimIndent(),
        )
    }
}

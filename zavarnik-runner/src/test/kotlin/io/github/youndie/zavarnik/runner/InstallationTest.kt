package io.github.youndie.zavarnik.runner

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class InstallationTest {
    private val app: File = Files.createTempDirectory("jib-app").toFile()
    private val javaHome = File("/opt/java/openjdk")

    private fun jibImage(classpath: String) {
        File(app, "jib-classpath-file").writeText(classpath)
        File(app, "jib-main-class-file").writeText("sample.MainKt\n")
        File(app, "classpath").mkdirs()
        File(app, "libs").mkdirs()
    }

    @Test
    fun `a packaged Jib image yields the jar directories, the entrypoint's command and no mtime pinning`() {
        jibImage("${app.path}/classpath/*:${app.path}/libs/a.jar:${app.path}/libs/b.jar")
        val installation = Installation.jib(app, javaHome, listOf("-Xmx256m"))
        assertEquals(listOf(File(app, "classpath"), File(app, "libs")), installation.jarDirs)
        assertEquals(File(app, "zavarnik"), installation.runnerDir)
        assertFalse(installation.pinsJarTimestamps)
        assertEquals(
            listOf(
                "/opt/java/openjdk/bin/java",
                "-Xmx256m",
                "-XX:AOTMode=on",
                "-cp",
                "@${app.path}/jib-classpath-file",
                "sample.MainKt",
            ),
            installation.launch.command(listOf("-XX:AOTMode=on")),
        )
    }

    @Test
    fun `the exploded layout is refused by name, with the fix`() {
        jibImage("${app.path}/resources:${app.path}/classes:${app.path}/libs/*")
        val failure = assertFailsWith<RunnerException> { Installation.jib(app, javaHome, emptyList()) }
        assertContains(failure.message!!, "directory on it (${app.path}/resources)")
        assertContains(failure.message!!, "containerizingMode = \"packaged\"")
    }

    @Test
    fun `a distribution is recognised by the absence of the classpath file`() {
        File(app, "bin").mkdirs()
        File(app, "bin/app").writeText("#!/bin/sh\n")
        File(app, "lib").mkdirs()
        val installation = Installation.detect(app, javaHome, emptyList())
        assertEquals(listOf(File(app, "lib")), installation.jarDirs)
        assertEquals(true, installation.pinsJarTimestamps)
        assertEquals(listOf(File(app, "bin/app").absolutePath), installation.launch.command(listOf("-Xlog:aot")))
        assertEquals("-Xlog:aot", installation.launch.environment(listOf("-Xlog:aot"))["JAVA_OPTS"])
    }
}

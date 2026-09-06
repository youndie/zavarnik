package io.github.youndie.zavarnik

import org.gradle.api.GradleException
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StartScriptGuardTest {
    private val unix =
        """
        DEFAULT_JVM_OPTS='"-Xmx1g"'

        # Collect all arguments for the java command:
        set -- -classpath "${'$'}CLASSPATH" demo.App "${'$'}@"
        """.trimIndent()

    @Test
    fun `unix guard goes right before the argument collection and names the cache file`() {
        val guarded = StartScriptGuard.unix(unix, "app.aot")
        val guardIndex = guarded.indexOf("if [ -f \"\$APP_HOME/lib/app.aot\" ]")
        val anchorIndex = guarded.indexOf(StartScriptGuard.UNIX_ANCHOR)
        assertTrue(guardIndex in 0 until anchorIndex, guarded)
        assertContains(guarded, "\\\"-XX:AOTCache=\$APP_HOME/lib/app.aot\\\"")
        assertContains(guarded, "DEFAULT_JVM_OPTS='\"-Xmx1g\"'")
    }

    @Test
    fun `windows guard follows DEFAULT_JVM_OPTS and keeps CRLF`() {
        val windows =
            "set APP_HOME=%DIRNAME%..\r\n" +
                "set DEFAULT_JVM_OPTS=\"-Xmx1g\"\r\n" +
                "set CLASSPATH=%APP_HOME%\\lib\\a.jar\r\n"
        val guarded = StartScriptGuard.windows(windows, "app.aot")
        val lines = guarded.split("\r\n")
        assertEquals("set DEFAULT_JVM_OPTS=\"-Xmx1g\"", lines[1])
        assertEquals(
            "if exist \"%APP_HOME%\\lib\\app.aot\" set DEFAULT_JVM_OPTS=%DEFAULT_JVM_OPTS% \"-XX:AOTCache=%APP_HOME%\\lib\\app.aot\"",
            lines[2],
        )
        assertEquals("set CLASSPATH=%APP_HOME%\\lib\\a.jar", lines[3])
    }

    @Test
    fun `a template without the anchor is refused by name`() {
        val failure = assertFailsWith<GradleException> { StartScriptGuard.unix("#!/bin/sh\nexec java", "app.aot") }
        assertContains(failure.message.orEmpty(), StartScriptGuard.UNIX_ANCHOR)
        assertFailsWith<GradleException> { StartScriptGuard.windows("@echo off\r\n", "app.aot") }
    }
}

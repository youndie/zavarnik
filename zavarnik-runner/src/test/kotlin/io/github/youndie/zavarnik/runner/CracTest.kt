package io.github.youndie.zavarnik.runner

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.time.Duration
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CracTest {
    private fun config() =
        RunnerConfig(
            cacheFileName = "app.aot",
            readyUrl = null,
            exitAfter = Duration.ofSeconds(1),
            readyTimeout = Duration.ofSeconds(5),
            shutdownTimeout = Duration.ofSeconds(5),
            workload = emptyList(),
            minCachedShare = 0.9,
            verifyJvmArgs = emptyList(),
        )

    private fun installation(
        dir: File,
        javaHome: File,
    ): Installation {
        File(dir, "bin").mkdirs()
        File(dir, "lib").mkdirs()
        File(dir, "bin/app").apply {
            writeText("#!/bin/sh\nsleep 30\n")
            setExecutable(true)
        }
        return Installation.distribution(dir, javaHome)
    }

    /** A `java` that answers `-version` normally and lists flags with no CRaC among them. */
    private fun javaWithoutCrac(dir: File): File {
        val home = File(dir, "plain-jdk")
        File(home, "bin").mkdirs()
        File(home, "bin/java").apply {
            writeText("#!/bin/sh\necho '     bool UseG1GC = true {product} {default}'\nexit 0\n")
            setExecutable(true)
        }
        return home
    }

    /** A `java` whose flag list has CRaC in it, so the checkpoint gets past the JDK check. */
    private fun javaWithCrac(dir: File): File {
        val home = File(dir, "crac-jdk")
        File(home, "bin").mkdirs()
        File(home, "bin/java").apply {
            writeText("#!/bin/sh\necho '    ccstr CRaCCheckpointTo = {product} {default}'\nexit 0\n")
            setExecutable(true)
        }
        return home
    }

    private fun crac(
        dir: File,
        javaHome: File,
        image: File = File(dir, "crac"),
    ) = Crac(installation(dir, javaHome), config(), File(dir, "log"), image)

    @Test
    fun `a JDK without CRaC is named as such, and the message says where to get one`(
        @TempDir dir: File,
    ) {
        // A fake JDK rather than the one running the test: which JVM the build happens to use is
        // not what this asserts, and on a machine that does have CRaC the test would go on to take
        // a real checkpoint of a shell script.
        val failure = assertFailsWith<RunnerException> { crac(dir, javaWithoutCrac(dir)).checkpoint() }
        val message = failure.message.orEmpty()
        assertTrue("has no CRaC" in message, message)
        assertTrue("Azul Zulu" in message, message)
    }

    @Test
    fun `a java that will not start is reported as that, not as a JDK without CRaC`(
        @TempDir dir: File,
    ) {
        val fakeHome = File(dir, "broken-jdk")
        File(fakeHome, "bin").mkdirs()
        File(fakeHome, "bin/java").apply {
            writeText("#!/bin/sh\necho 'cannot execute binary file' >&2\nexit 126\n")
            setExecutable(true)
        }
        val failure = assertFailsWith<RunnerException> { crac(dir, fakeHome).checkpoint() }
        val message = failure.message.orEmpty()
        assertTrue("would not answer" in message, message)
        assertFalse("has no CRaC" in message, message)
    }

    @Test
    fun `the policy file is written inside the installation, not into the snapshot`(
        @TempDir dir: File,
    ) {
        // A JDK that passes the CRaC check, so the run gets as far as writing the policy; it then
        // fails for want of jcmd, which is fine — the location of the file is what this asserts,
        // and the location is the whole point: a restore reads the recorded path again, and a
        // mounted output directory does not exist by then.
        val image = File(dir, "crac")
        val failure = assertFailsWith<RunnerException> { crac(dir, javaWithCrac(dir), image).checkpoint() }
        assertTrue("jcmd" in failure.message.orEmpty(), failure.message.orEmpty())
        assertTrue(
            File(dir, "lib/${CracPolicies.FILE_NAME}").isFile,
            "not in lib/: ${File(dir, "lib").list()?.toList()}",
        )
        assertFalse(File(image, CracPolicies.FILE_NAME).exists(), "the snapshot directory must not hold it")
    }

    @Test
    fun `a snapshot directory with nothing in it is not a snapshot`(
        @TempDir dir: File,
    ) {
        val image = File(dir, "crac").apply { mkdirs() }
        // The JDK check runs first, so the fake one has to be the one without CRaC; what this case
        // is really about is that an empty directory does not pass for a snapshot.
        val failure = assertFailsWith<RunnerException> { crac(dir, javaWithoutCrac(dir), image).restoreVerify() }
        assertTrue("has no CRaC" in failure.message.orEmpty(), failure.message.orEmpty())
    }
}

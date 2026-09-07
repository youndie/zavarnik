package io.github.youndie.zavarnik.runner

import java.io.File

/**
 * How the application is started: the two layouts the runner knows.
 *
 * Whatever the layout, the JVM records in the cache the classpath string it was started with, in
 * that order, and compares it at the next start — so the training run has to present the string
 * production will present. For a distribution that is the start script; for a Jib image it is the
 * command Jib wrote into the entrypoint.
 */
public sealed interface Launch {
    /** The command line, with [jvmArgs] first, then whatever launches the application. */
    public fun command(jvmArgs: List<String>): List<String>

    /** Working directory for the process. */
    public val directory: File

    /** Environment the process gets on top of the runner's own. */
    public fun environment(jvmArgs: List<String>): Map<String, String>

    /**
     * The `application` plugin's start script. JVM flags for a run travel through `JAVA_OPTS`,
     * which the script folds into the command line after its own `DEFAULT_JVM_OPTS` — where the
     * user's `jvmArgs` and the portability flags already are.
     */
    public class Script(
        public val script: File,
        private val javaHome: File,
    ) : Launch {
        override fun command(jvmArgs: List<String>): List<String> = listOf(script.absolutePath)

        override val directory: File get() = script.parentFile.parentFile

        override fun environment(jvmArgs: List<String>): Map<String, String> =
            mapOf("JAVA_HOME" to javaHome.absolutePath, "JAVA_OPTS" to jvmArgs.joinToString(" "))
    }

    /**
     * `java <baseJvmArgs> <jvmArgs> -cp @<classpathFile> <mainClass>` — the shape of a Jib
     * entrypoint. [baseJvmArgs] are the flags the image starts with in production (Jib's
     * `jvmFlags`, the user's `jvmArgs`, the portability flags); a run adds its own after them.
     */
    public class Command(
        private val java: File,
        private val baseJvmArgs: List<String>,
        private val classpathArgument: String,
        private val mainClass: String,
        override val directory: File,
    ) : Launch {
        override fun command(jvmArgs: List<String>): List<String> =
            listOf(java.absolutePath) + baseJvmArgs + jvmArgs + listOf("-cp", classpathArgument, mainClass)

        override fun environment(jvmArgs: List<String>): Map<String, String> = emptyMap()
    }
}

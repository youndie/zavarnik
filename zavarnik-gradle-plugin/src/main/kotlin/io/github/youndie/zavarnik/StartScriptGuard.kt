package io.github.youndie.zavarnik

import org.gradle.api.GradleException

/**
 * The lines the plugin adds to the start scripts Gradle generates: add `-XX:AOTCache` **only when
 * the cache file exists**.
 *
 * Unconditional would be simpler and is wrong twice over. `$APP_HOME` cannot be written into
 * `DEFAULT_JVM_OPTS` through the `application` extension — the script template itself says the
 * only way is to post-process the script, and it escapes any shell fragment it is handed. And a
 * script that always passes `-XX:AOTCache` cannot be the training launcher: the JVM refuses that
 * flag next to `-XX:AOTCacheOutput`. With the guard, one script serves training (no cache yet, no
 * flag), verification and production; a distribution shipped without a cache runs without the
 * flag and without the three `[error][aot]` lines the JVM prints for a missing file.
 */
public object StartScriptGuard {
    /** The line in Gradle's unix template before which the guard goes. Anchored on text, not on a line number. */
    public const val UNIX_ANCHOR: String = "# Collect all arguments for the java command:"

    /** The line in Gradle's windows template after which the guard goes. */
    public const val WINDOWS_ANCHOR_PREFIX: String = "set DEFAULT_JVM_OPTS="

    /** The unix script with the guard, or an exception naming the template change that removed the anchor. */
    public fun unix(
        script: String,
        cacheFileName: String,
    ): String {
        if (UNIX_ANCHOR !in script) throw missingAnchor("unix", UNIX_ANCHOR)
        refuseWildcard(script)
        val guard =
            """
            |if [ -f "${'$'}APP_HOME/lib/$cacheFileName" ]; then
            |    DEFAULT_JVM_OPTS="${'$'}DEFAULT_JVM_OPTS \"-XX:AOTCache=${'$'}APP_HOME/lib/$cacheFileName\""
            |fi
            |
            |$UNIX_ANCHOR
            """.trimMargin()
        return script.replace(UNIX_ANCHOR, guard)
    }

    /** The windows script with the guard, keeping the script's own line separator. */
    public fun windows(
        script: String,
        cacheFileName: String,
    ): String {
        val separator = if ("\r\n" in script) "\r\n" else "\n"
        val lines = script.split(separator)
        val index = lines.indexOfFirst { it.startsWith(WINDOWS_ANCHOR_PREFIX) }
        if (index < 0) throw missingAnchor("windows", WINDOWS_ANCHOR_PREFIX)
        refuseWildcard(script)
        val guard =
            "if exist \"%APP_HOME%\\lib\\$cacheFileName\" " +
                "set DEFAULT_JVM_OPTS=%DEFAULT_JVM_OPTS% \"-XX:AOTCache=%APP_HOME%\\lib\\$cacheFileName\""
        return (lines.take(index + 1) + guard + lines.drop(index + 1)).joinToString(separator)
    }

    /**
     * A wildcard on the classpath is refused. The JVM expands a `lib` wildcard in directory order,
     * which is whatever the filesystem answers — and that differs between container runtimes: a cache
     * trained under Docker's overlay2 on a CI runner recorded `ktor-http-cio` first, the k0s node's
     * containerd handed the JVM `packages-shared-api` first, and production rejected the cache with
     * "The name of app classpath [1] does not match" and started without it, silently. `aotVerify`
     * cannot catch it — it runs where the training ran. A listed classpath is the same string
     * everywhere.
     */
    private fun refuseWildcard(script: String) {
        if (WILDCARD.containsMatchIn(script)) {
            throw GradleException(
                "zavarnik: the start script's CLASSPATH has a wildcard (`lib/*`). The JVM expands it in directory " +
                    "order, which differs between filesystems and container runtimes, and a cache trained on one " +
                    "is rejected on the other — silently, in production. List the jars: leave " +
                    "`startScripts.classpath` alone, or set it to the files by name.",
            )
        }
    }

    private val WILDCARD = Regex("""CLASSPATH=.*[/\\]\*""")

    private fun missingAnchor(
        which: String,
        anchor: String,
    ) = GradleException(
        "zavarnik: the $which start script has no line `$anchor` to anchor the AOT cache guard on. " +
            "Gradle's start-script template has changed; the plugin needs an update for this Gradle version.",
    )
}

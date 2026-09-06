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
        val guard =
            "if exist \"%APP_HOME%\\lib\\$cacheFileName\" " +
                "set DEFAULT_JVM_OPTS=%DEFAULT_JVM_OPTS% \"-XX:AOTCache=%APP_HOME%\\lib\\$cacheFileName\""
        return (lines.take(index + 1) + guard + lines.drop(index + 1)).joinToString(separator)
    }

    private fun missingAnchor(
        which: String,
        anchor: String,
    ) = GradleException(
        "zavarnik: the $which start script has no line `$anchor` to anchor the AOT cache guard on. " +
            "Gradle's start-script template has changed; the plugin needs an update for this Gradle version.",
    )
}

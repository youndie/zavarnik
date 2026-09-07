package io.github.youndie.zavarnik.runner

import java.io.File
import java.time.Duration
import java.util.Properties

/**
 * Everything the runner needs to train and verify, as `lib/zavarnik.properties` next to the cache.
 * The plugin writes it from the `zavarnik { }` block; `java -cp lib/zavarnik-runner.jar … train`
 * reads it. `Properties` rather than JSON so that the runner carries no dependency.
 */
public data class RunnerConfig(
    val cacheFileName: String,
    val readyUrl: String?,
    val exitAfter: Duration?,
    val readyTimeout: Duration,
    val shutdownTimeout: Duration,
    val workload: List<WorkloadStep>,
    val minCachedShare: Double,
    val verifyJvmArgs: List<String>,
    /**
     * For a Jib image only: the flags the image starts with (Jib's `jvmFlags`, the user's `jvmArgs`,
     * the portability flags), which the runner has to put on its own command line — a distribution's
     * start script carries them itself. Empty for a distribution.
     */
    val launchJvmArgs: List<String> = emptyList(),
) {
    /** `<cacheFileName>.jars`. */
    val manifestFileName: String get() = "$cacheFileName.jars"

    public fun write(file: File) {
        val props = Properties()
        props["cacheFileName"] = cacheFileName
        readyUrl?.let { props["readyUrl"] = it }
        exitAfter?.let { props["exitAfterMillis"] = it.toMillis().toString() }
        props["readyTimeoutMillis"] = readyTimeout.toMillis().toString()
        props["shutdownTimeoutMillis"] = shutdownTimeout.toMillis().toString()
        props["minCachedShare"] = minCachedShare.toString()
        props["verifyJvmArgs"] = verifyJvmArgs.joinToString(SEPARATOR)
        props["launchJvmArgs"] = launchJvmArgs.joinToString(SEPARATOR)
        workload.forEachIndexed { i, step ->
            val key = "workload.$i"
            if (step.isCommand) {
                props["$key.command"] = step.command.joinToString(SEPARATOR)
            } else {
                props["$key.method"] = step.method
                props["$key.url"] = step.url
                step.contentType?.let { props["$key.contentType"] = it }
                step.body?.let { props["$key.body"] = it }
                for ((name, value) in step.headers) props["$key.header.$name"] = value
                for ((variable, path) in step.captures) props["$key.capture.$variable"] = path
            }
        }
        file.parentFile.mkdirs()
        file.outputStream().use { props.store(it, "zavarnik runner configuration, written by the Gradle plugin") }
    }

    public companion object {
        /** The file name inside `lib/`. */
        public const val FILE_NAME: String = "zavarnik.properties"

        /** ASCII unit separator: never part of a URL, a flag or a command word, so lists survive it. */
        private const val SEPARATOR = ""

        public fun read(file: File): RunnerConfig {
            if (!file.isFile) {
                throw RunnerException(
                    "zavarnik: no ${file.name} in ${file.parentFile.path} — was the distribution built with the plugin?",
                )
            }
            val props = Properties().apply { file.inputStream().use { load(it) } }

            fun millis(key: String): Duration? = props.getProperty(key)?.let { Duration.ofMillis(it.toLong()) }

            fun list(key: String): List<String> = props.getProperty(key, "").split(SEPARATOR).filter { it.isNotEmpty() }

            fun byPrefix(prefix: String): Map<String, String> =
                props
                    .stringPropertyNames()
                    .filter { it.startsWith(prefix) }
                    .sorted()
                    .associate { it.removePrefix(prefix) to props.getProperty(it) }
            val steps = ArrayList<WorkloadStep>()
            var i = 0
            while (props.containsKey("workload.$i.command") || props.containsKey("workload.$i.url")) {
                val key = "workload.$i"
                steps +=
                    if (props.containsKey("$key.command")) {
                        WorkloadStep.command(list("$key.command"))
                    } else {
                        WorkloadStep.http(
                            props.getProperty("$key.method"),
                            props.getProperty("$key.url"),
                            props.getProperty("$key.contentType"),
                            props.getProperty("$key.body"),
                            headers = byPrefix("$key.header."),
                            captures = byPrefix("$key.capture."),
                        )
                    }
                i++
            }
            return RunnerConfig(
                cacheFileName = props.getProperty("cacheFileName", DEFAULT_CACHE_FILE_NAME),
                readyUrl = props.getProperty("readyUrl"),
                exitAfter = millis("exitAfterMillis"),
                readyTimeout = millis("readyTimeoutMillis") ?: Duration.ofMinutes(DEFAULT_READY_TIMEOUT_MINUTES),
                shutdownTimeout =
                    millis("shutdownTimeoutMillis") ?: Duration.ofMinutes(DEFAULT_SHUTDOWN_TIMEOUT_MINUTES),
                workload = steps,
                minCachedShare = props.getProperty("minCachedShare", DEFAULT_MIN_CACHED_SHARE).toDouble(),
                verifyJvmArgs = list("verifyJvmArgs"),
                launchJvmArgs = list("launchJvmArgs"),
            )
        }

        private const val DEFAULT_CACHE_FILE_NAME = "app.aot"
        private const val DEFAULT_READY_TIMEOUT_MINUTES = 2L
        private const val DEFAULT_SHUTDOWN_TIMEOUT_MINUTES = 5L
        private const val DEFAULT_MIN_CACHED_SHARE = "0.9"
    }
}

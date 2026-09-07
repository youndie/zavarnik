package io.github.youndie.zavarnik.runner

import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * One run of the application through its start script: start, wait for readiness, stop with
 * `SIGTERM`, wait for the launcher.
 *
 * The script, not `java` directly, because the classpath string the JVM records in the cache is
 * the one the script builds, in the script's order — the only way to train against the string
 * production will present is to run production's launcher (research D1). JVM flags for this run
 * travel through `JAVA_OPTS`, which the script folds into the command line after its own
 * `DEFAULT_JVM_OPTS`.
 */
public class StartScriptRun(
    private val script: File,
    private val javaHome: File,
    private val javaOpts: List<String>,
    private val log: File,
) {
    private lateinit var process: Process

    /** Nanoseconds since the process was started. Monotonic on purpose: this measures durations. */
    private var startedAt: Long = 0

    public fun start() {
        if (System.getProperty("os.name").startsWith("Windows")) {
            throw RunnerException(
                "zavarnik: training on Windows is not supported yet — the training run is stopped with " +
                    "SIGTERM, which Windows does not have, and a killed JVM writes no cache.",
            )
        }
        log.parentFile.mkdirs()
        val builder =
            ProcessBuilder(script.absolutePath)
                .directory(script.parentFile.parentFile)
                .redirectErrorStream(true)
                .redirectOutput(log)
        builder.environment()["JAVA_HOME"] = javaHome.absolutePath
        builder.environment()["JAVA_OPTS"] = javaOpts.joinToString(" ")
        startedAt = System.nanoTime()
        process = builder.start()
    }

    /** Milliseconds since [start]. */
    public fun elapsedMillis(): Long = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)

    public val isAlive: Boolean get() = process.isAlive

    /**
     * Polls [url] until it answers `200`, or [timeout] passes, or the process dies. Returns the
     * milliseconds it took; throws with the log's tail otherwise.
     */
    public fun awaitReady(
        url: String,
        timeout: Duration,
    ): Long {
        val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build()
        val request =
            HttpRequest
                .newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(2))
                .GET()
                .build()
        val deadline = System.nanoTime() + timeout.toNanos()
        while (System.nanoTime() < deadline) {
            if (!process.isAlive) {
                throw RunnerException("zavarnik: the application exited before $url answered.\n${logTail()}")
            }
            if (probe(client, request) == HTTP_OK) return elapsedMillis()
            Thread.sleep(POLL_INTERVAL_MILLIS)
        }
        kill()
        throw RunnerException(
            "zavarnik: $url did not answer 200 within ${timeout.toSeconds()} s (readyTimeout).\n${logTail()}",
        )
    }

    private fun probe(
        client: HttpClient,
        request: HttpRequest,
    ): Int =
        try {
            client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode()
        } catch (notUpYet: java.io.IOException) {
            // Connection refused while the port is not open yet, or reset while the server starts.
            // Either way: not ready, ask again.
            -1
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            throw RunnerException("zavarnik: interrupted while waiting for readiness", interrupted)
        }

    /**
     * `SIGTERM`, then wait up to [timeout] for the process to exit. A process that does not exit
     * is killed — and the task that asked for this fails, because a killed JVM writes no cache.
     */
    public fun stop(timeout: Duration) {
        if (!process.isAlive) return
        process.destroy()
        if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            process.destroyForcibly()
            throw RunnerException(
                "zavarnik: the application did not exit within ${timeout.toSeconds()} s of SIGTERM " +
                    "(shutdownTimeout); it was killed with SIGKILL, which writes no cache.\n${logTail()}",
            )
        }
    }

    /**
     * `SIGKILL`, for the failure paths. A run that never became ready or whose workload failed must
     * leave no cache behind — a half-trained cache is a half-truth — and a killed JVM writes none,
     * with no assembler child to race the deletion.
     */
    public fun kill() {
        if (!process.isAlive) return
        process.destroyForcibly()
        process.waitFor(KILL_WAIT_SECONDS, TimeUnit.SECONDS)
    }

    /** Last lines of the process log, for an error message. */
    public fun logTail(lines: Int = LOG_TAIL_LINES): String =
        if (log.exists()) {
            log
                .readLines()
                .takeLast(
                    lines,
                ).joinToString("\n", prefix = "--- ${log.name}, last $lines lines:\n")
        } else {
            ""
        }

    private companion object {
        const val HTTP_OK = 200
        const val POLL_INTERVAL_MILLIS = 50L
        const val LOG_TAIL_LINES = 30
        const val KILL_WAIT_SECONDS = 10L
    }
}

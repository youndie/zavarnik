package io.github.youndie.zavarnik.runner

import java.io.File
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * Runs [WorkloadStep]s against a started application: HTTP requests through the plugin's own
 * client, commands through the process API. Shared by the training run, which runs the steps
 * once, and the report, which loops over them for a while to give the JIT something to do.
 *
 * Values a step [WorkloadStep.captures] live here for the steps after it, as `{{name}}`.
 */
public class Workload(
    private val log: File,
) : AutoCloseable {
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS)).build()
    private val variables = ConcurrentHashMap<String, String>()

    /** Runs one step; a failure is a [RunnerException] naming the step and the reason. */
    public fun run(step: WorkloadStep) {
        if (step.isCommand) runCommand(step.command.map { expand(it, step) }) else runRequest(step)
    }

    private fun runCommand(command: List<String>) {
        val process =
            try {
                ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.appendTo(log))
                    .start()
            } catch (notFound: IOException) {
                throw RunnerException(
                    "zavarnik: workload command `${command.first()}` cannot be started here (${notFound.message}). " +
                        "Inside a container or on a bare runner prefer `workload { get(…) }` / `post(…)`, " +
                        "which need nothing installed.",
                    notFound,
                )
            }
        val exit = process.waitFor()
        if (exit != 0) {
            throw RunnerException("zavarnik: workload command ${command.joinToString(" ")} exited with $exit.")
        }
    }

    private fun runRequest(step: WorkloadStep) {
        val request =
            HttpRequest
                .newBuilder(URI.create(expand(step.url, step)))
                .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                .apply {
                    for ((name, value) in step.headers) header(name, expand(value, step))
                    if (step.method == "POST") {
                        header("Content-Type", step.contentType ?: "application/octet-stream")
                        POST(HttpRequest.BodyPublishers.ofString(expand(step.body.orEmpty(), step)))
                    } else {
                        GET()
                    }
                }.build()
        val response =
            try {
                http.send(request, HttpResponse.BodyHandlers.ofString())
            } catch (failed: IOException) {
                throw RunnerException("zavarnik: workload request $step failed: ${failed.message}", failed)
            } catch (interrupted: InterruptedException) {
                Thread.currentThread().interrupt()
                throw RunnerException("zavarnik: interrupted during the workload", interrupted)
            }
        if (response.statusCode() !in HTTP_OK_RANGE) {
            throw RunnerException("zavarnik: workload request $step answered ${response.statusCode()}.")
        }
        if (step.captures.isNotEmpty()) capture(step, response.body())
    }

    private fun capture(
        step: WorkloadStep,
        body: String,
    ) {
        val json =
            try {
                Json.parse(body)
            } catch (notJson: RunnerException) {
                throw RunnerException(
                    "zavarnik: workload request $step did not answer JSON, nothing to capture: ${notJson.message}",
                    notJson,
                )
            }
        for ((variable, path) in step.captures) {
            variables[variable] =
                try {
                    Json.extract(json, path)
                } catch (missing: RunnerException) {
                    throw RunnerException(
                        "zavarnik: workload request $step: cannot capture `$variable` — ${missing.message}",
                        missing,
                    )
                }
        }
    }

    /**
     * Closes the connections this client is keeping alive.
     *
     * It matters for one caller and not at all for the rest: a CRaC checkpoint refuses while any
     * socket is open, and the sockets the *server* accepted from this client are open exactly
     * because HTTP keep-alive is doing its job. Found on the Ktor sample, where a checkpoint after
     * a clean workload failed in `ServerSocketChannelImpl.finishAccept` — the accepted end of the
     * workload's own connection.
     */
    override fun close() {
        http.close()
    }

    /** `{{name}}` → the captured value; a name nothing captured is a mistake in the workload, not an empty string. */
    private fun expand(
        text: String,
        step: WorkloadStep,
    ): String =
        PLACEHOLDER.replace(text) { match ->
            val name = match.groupValues[1]
            variables[name]
                ?: throw RunnerException(
                    "zavarnik: workload step $step uses {{$name}}, which no earlier step captured " +
                        "(captured so far: ${variables.keys.sorted()}).",
                )
        }

    private companion object {
        const val REQUEST_TIMEOUT_SECONDS = 30L
        val HTTP_OK_RANGE = 200..299
        val PLACEHOLDER = Regex("""\{\{\s*([A-Za-z_][A-Za-z0-9_]*)\s*}}""")
    }
}

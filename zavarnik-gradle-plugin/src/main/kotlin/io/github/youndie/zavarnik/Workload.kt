package io.github.youndie.zavarnik

import org.gradle.api.GradleException
import java.io.File
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Runs [WorkloadStep]s against a started application: HTTP requests through the plugin's own
 * client, commands through the process API. Shared by the training run, which runs the steps
 * once, and the report, which loops over them for a while to give the JIT something to do.
 */
internal class Workload(
    private val log: File,
) {
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS)).build()

    /** Runs one step; a failure is a [GradleException] naming the step and the reason. */
    fun run(step: WorkloadStep) {
        if (step.isCommand) runCommand(step.command) else runRequest(step)
    }

    private fun runCommand(command: List<String>) {
        val process =
            try {
                ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.appendTo(log))
                    .start()
            } catch (notFound: IOException) {
                throw GradleException(
                    "zavarnik: workload command `${command.first()}` cannot be started here (${notFound.message}). " +
                        "Inside a container or on a bare runner prefer `workload { get(…) }` / `post(…)`, " +
                        "which need nothing installed.",
                    notFound,
                )
            }
        val exit = process.waitFor()
        if (exit !=
            0
        ) {
            throw GradleException("zavarnik: workload command ${command.joinToString(" ")} exited with $exit.")
        }
    }

    private fun runRequest(step: WorkloadStep) {
        val request =
            HttpRequest
                .newBuilder(URI.create(step.url))
                .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                .apply {
                    if (step.method == "POST") {
                        header("Content-Type", step.contentType ?: "application/octet-stream")
                        POST(HttpRequest.BodyPublishers.ofString(step.body.orEmpty()))
                    } else {
                        GET()
                    }
                }.build()
        val status =
            try {
                http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode()
            } catch (failed: IOException) {
                throw GradleException("zavarnik: workload request $step failed: ${failed.message}", failed)
            } catch (interrupted: InterruptedException) {
                Thread.currentThread().interrupt()
                throw GradleException("zavarnik: interrupted during the workload", interrupted)
            }
        if (status !in HTTP_OK_RANGE) throw GradleException("zavarnik: workload request $step answered $status.")
    }

    private companion object {
        const val REQUEST_TIMEOUT_SECONDS = 30L
        val HTTP_OK_RANGE = 200..299
    }
}

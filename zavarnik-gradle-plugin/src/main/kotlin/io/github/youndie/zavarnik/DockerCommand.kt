package io.github.youndie.zavarnik

import org.gradle.api.GradleException
import java.io.File
import java.io.IOException

/**
 * `docker …` from a task: output to a log file, a non-zero exit reported with the log's tail. The
 * Jib mode needs a Docker daemon on the build machine — the one thing Jib let a build do without,
 * and the one thing training inside the image cannot do without.
 */
internal object DockerCommand {
    fun run(
        args: List<String>,
        log: File,
        what: String,
    ) {
        log.parentFile.mkdirs()
        val process =
            try {
                ProcessBuilder(listOf("docker") + args)
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.appendTo(log))
                    .start()
            } catch (notFound: IOException) {
                throw GradleException(
                    "zavarnik: `docker` cannot be started here (${notFound.message}). $what runs the image in a " +
                        "container, so the Jib mode needs a Docker daemon on this machine.",
                    notFound,
                )
            }
        val exit = process.waitFor()
        if (exit != 0) {
            throw GradleException("zavarnik: $what failed (docker exited with $exit).\n${tail(log)}")
        }
    }

    /** The user and group to run the container as, so that what it writes into a mounted directory belongs to the host user. */
    fun hostUser(): String {
        val uid = read(listOf("id", "-u"))
        val gid = read(listOf("id", "-g"))
        return "$uid:$gid"
    }

    private fun read(command: List<String>): String {
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val text =
            process.inputStream
                .bufferedReader()
                .readText()
                .trim()
        if (process.waitFor() != 0) throw GradleException("zavarnik: `${command.joinToString(" ")}` failed: $text")
        return text
    }

    fun tail(
        log: File,
        lines: Int = TAIL_LINES,
    ): String =
        if (log.exists()) {
            log.readLines().takeLast(lines).joinToString("\n", prefix = "--- ${log.name}, last $lines lines:\n")
        } else {
            ""
        }

    private const val TAIL_LINES = 40
}

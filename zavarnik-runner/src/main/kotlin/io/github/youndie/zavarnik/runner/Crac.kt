package io.github.youndie.zavarnik.runner

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * A CRaC checkpoint of the warmed-up application, and the restore that proves it.
 *
 * The shape is the AOT phase's, with the result changed: instead of a cache of classes the
 * training run leaves a snapshot of the process, and instead of starting the application again
 * under `-XX:AOTMode=on` the verification *restores* it. What survives from there is the
 * measurement in `docs/research/research-crac.md`: the restore is ready in a fraction of the time
 * and its JIT is warm, and what breaks is every open socket — which [CracPolicies] answers.
 *
 * The snapshot is not a file but a directory, and it belongs to the image it was taken in, down to
 * the build id of every mapped file. It is written where the caller says, so that the host can lay
 * it over that image as one more layer.
 */
public class Crac(
    private val installation: Installation,
    private val config: RunnerConfig,
    private val log: File,
    private val imageDir: File,
    private val report: (String) -> Unit = ::println,
) {
    /** Warm the application up and take the snapshot. Leaves [imageDir] holding it. */
    public fun checkpoint() {
        requireCracJdk()
        if (config.readyUrl == null && config.exitAfter == null) {
            throw RunnerException(
                "zavarnik: a checkpoint needs a way to know the application is warm — set " +
                    "`training { readyWhen.url(\"http://…/health\") }` or `training { exitAfter = … }`.",
            )
        }
        imageDir.deleteRecursively()
        imageDir.mkdirs()
        val policies = CracPolicies.write(File(imageDir, CracPolicies.FILE_NAME), config.cracIgnoredRemotePorts)
        if (config.cracIgnoredRemotePorts.isNotEmpty()) report(CracPolicies.WARNING)
        val run =
            ApplicationRun(
                installation.launch,
                listOf(
                    "-XX:CRaCCheckpointTo=${imageDir.absolutePath}",
                    "-Djdk.crac.resource-policies=${policies.absolutePath}",
                    // Names the thread that opened a socket the checkpoint then trips over. Without
                    // it the refusal says which socket and not who owns it, and "who" is the fix.
                    "-Djdk.crac.collect-fd-stacktraces=true",
                ),
                log,
            )
        run.start()
        try {
            Exercise.run(run, config, log, report)
        } catch (failed: RunnerException) {
            run.kill()
            imageDir.deleteRecursively()
            throw failed
        }
        // The workload closed its connections; the server has not necessarily noticed yet. A
        // checkpoint taken in that gap fails on the accepted end of a connection whose client is
        // already gone — measured on the Ktor sample, where the refusal points at
        // `ServerSocketChannelImpl.finishAccept`. Waiting is cheaper than a policy rule that would
        // close live connections along with dead ones.
        Thread.sleep(SETTLE_MILLIS)
        try {
            takeSnapshot(run)
        } catch (failed: RunnerException) {
            // Half a snapshot passes the "is there one?" test the restore starts with, and then
            // fails somewhere less legible. A training run that ends badly leaves no cache for the
            // same reason.
            imageDir.deleteRecursively()
            throw failed
        }
        report("zavarnik: snapshot in ${imageDir.name} is ${sizeKib()} KiB across ${imageFiles().size} files")
    }

    /**
     * Restore the snapshot and put the restored process through the same workload.
     *
     * The workload is the check, not the start: `ignore` leaves outgoing sockets pointing at
     * `/dev/null` after a restore, so a process that is up proves nothing about the pool behind
     * it — only a request that reaches the database does.
     */
    public fun restoreVerify(): String {
        requireCracJdk()
        if (imageFiles().isEmpty()) {
            throw RunnerException(
                "zavarnik: no CRaC snapshot in ${imageDir.path} — run the checkpoint first.",
            )
        }
        val run =
            ApplicationRun(
                Launch.Restore(File(installation.javaHome, "bin/java"), imageDir, installation.dir),
                emptyList(),
                log,
            )
        run.start()
        val readyMillis =
            try {
                Exercise.run(run, config, log, report)
            } catch (failed: RunnerException) {
                run.kill()
                throw RunnerException(
                    "${failed.message}\nzavarnik: the restored process did not serve the workload. A restore " +
                        "keeps the sockets it was told to ignore, and whoever owns them has to notice: a " +
                        "connection pool that validates on borrow does, one that does not hands out a dead " +
                        "connection.",
                    failed,
                )
            }
        run.stop(config.shutdownTimeout)
        return "restored and served the workload, ready after $readyMillis ms"
    }

    private fun takeSnapshot(run: ApplicationRun) {
        val jcmd = File(installation.javaHome, "bin/jcmd")
        if (!jcmd.canExecute()) {
            run.kill()
            throw RunnerException(
                "zavarnik: no jcmd at ${jcmd.path}, and a checkpoint is asked for through it. Zulu's " +
                    "`-jre-crac` images carry jcmd; an ordinary JRE image does not.",
            )
        }
        val started = System.nanoTime()
        val jcmdLog = File(log.parentFile, "${log.nameWithoutExtension}.jcmd.log")
        val checkpoint =
            ProcessBuilder(jcmd.absolutePath, run.pid().toString(), "JDK.checkpoint")
                .redirectErrorStream(true)
                .redirectOutput(jcmdLog)
                .start()
        checkpoint.waitFor(config.shutdownTimeout.toMillis(), TimeUnit.MILLISECONDS)
        // A checkpoint ends the process it was taken of. Waiting for that is how we know the
        // snapshot is finished — the image directory has files in it long before it is complete.
        if (!run.awaitExit(config.shutdownTimeout)) {
            run.kill()
            throw RunnerException(
                "zavarnik: the application was still running ${config.shutdownTimeout.toSeconds()} s after " +
                    "JDK.checkpoint. What jcmd said:\n${jcmdLog.takeIf {
                        it.isFile
                    }?.readText().orEmpty()}\n${run.logTail()}",
            )
        }
        val jcmdOutput = jcmdLog.takeIf { it.isFile }?.readText().orEmpty()
        if (imageFiles().isEmpty()) {
            throw RunnerException(
                "zavarnik: no snapshot was written to ${imageDir.path}. What jcmd said:\n$jcmdOutput\n" +
                    "An open socket is the usual reason, and the JVM names the thread that opened it — " +
                    "declare its port in `crac { ignoreRemotePort(…) }` and try again.\n${run.logTail()}",
            )
        }
        report("zavarnik: checkpoint took ${TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)} ms")
    }

    /** The snapshot's own files, which is everything in the directory except the policy file. */
    private fun imageFiles(): List<File> =
        imageDir.listFiles().orEmpty().filter { it.isFile && it.name != CracPolicies.FILE_NAME }

    private fun sizeKib(): Long = imageFiles().sumOf { it.length() } / KIB

    /**
     * A JDK without CRaC refuses `-XX:CRaCCheckpointTo` as an unrecognised option, which reads like
     * a bug in the plugin rather than a JDK without the feature. Ask the JVM what flags it has and
     * say the real thing instead.
     */
    private fun requireCracJdk() {
        val java = File(installation.javaHome, "bin/java")
        val process =
            ProcessBuilder(java.absolutePath, "-XX:+PrintFlagsFinal", "-version")
                .redirectErrorStream(true)
                .start()
        val flags = process.inputStream.bufferedReader().readText()
        // A JVM that would not start at all is a different fault, and saying "no CRaC" about it
        // sends the reader to the wrong page. Its own output is the better message.
        if (process.waitFor() != 0) {
            throw RunnerException(
                "zavarnik: ${java.path} would not answer `-version` (exit ${process.exitValue()}):\n" +
                    flags.lines().takeLast(FLAG_ERROR_LINES).joinToString("\n"),
            )
        }
        if ("CRaCCheckpointTo" !in flags) {
            throw RunnerException(
                "zavarnik: ${installation.javaHome.path} has no CRaC — `-XX:CRaCCheckpointTo` is not one of its " +
                    "flags. CRaC ships in Azul Zulu (17 through 26) and BellSoft Liberica (17 and 21), on Linux; " +
                    "an ordinary Temurin cannot take a checkpoint.",
            )
        }
    }

    private companion object {
        const val KIB = 1024

        /** How long the server is given to drop the connections the workload has just closed. */
        const val SETTLE_MILLIS = 2_000L

        /** Lines of a failed `java -version` worth quoting back. */
        const val FLAG_ERROR_LINES = 5
    }
}

package io.github.youndie.zavarnik.runner

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Wait for the application to be up, then put it through the configured workload.
 *
 * The same two steps for an AOT training run and for a CRaC checkpoint, which differ only in what
 * they do afterwards — assemble a cache, or take a snapshot. It also runs on the *restored*
 * process, where its meaning changes: there it is the check that the restore is alive in the way
 * that matters, because a workload step reaches the database through the pool whose sockets the
 * snapshot left pointing at `/dev/null`.
 */
public object Exercise {
    /** Returns the milliseconds to readiness; throws [RunnerException] with the log's tail. */
    public fun run(
        run: ApplicationRun,
        config: RunnerConfig,
        log: File,
        report: (String) -> Unit,
    ): Long {
        val readyMillis =
            if (config.readyUrl != null) {
                run.awaitReady(config.readyUrl, config.readyTimeout)
            } else {
                Thread.sleep(config.exitAfter!!.toMillis())
                run.elapsedMillis()
            }
        report("zavarnik: application ready after $readyMillis ms")
        val started = System.nanoTime()
        val workload = Workload(File(log.parentFile, "${log.nameWithoutExtension}.workload.log"))
        try {
            for (step in config.workload) {
                try {
                    workload.run(step)
                } catch (failed: RunnerException) {
                    throw RunnerException("${failed.message}\n${run.logTail()}", failed)
                }
            }
        } finally {
            // Keep-alive is the default and the right default; it is also an open socket on the
            // server's side, which is the one thing a CRaC checkpoint will not tolerate.
            workload.close()
        }
        report("zavarnik: workload took ${TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)} ms")
        return readyMillis
    }
}

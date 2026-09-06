package io.github.youndie.zavarnik

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.jvm.toolchain.JavaLauncher
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.time.Duration

/**
 * Cold versus cached: time to the first `200` on the readiness URL, measured from outside the
 * process, [runs] times each way, as sorted rows and medians in a markdown table.
 *
 * Medians and full rows, not means: the first run after a pause is regularly several times
 * slower than the rest, and one such outlier moves a mean by more than the cache does. Both
 * columns are milliseconds — a percentage without the absolute numbers decides nothing.
 */
@DisableCachingByDefault(because = "a measurement of this machine, right now")
public abstract class AotReportTask : DefaultTask() {
    @get:Internal
    public abstract val installDir: DirectoryProperty

    @get:Input
    public abstract val scriptName: Property<String>

    @get:Nested
    public abstract val javaLauncher: Property<JavaLauncher>

    @get:Internal
    public abstract val cacheFile: RegularFileProperty

    @get:Input
    public abstract val readyUrl: Property<String>

    @get:Input
    public abstract val readyTimeout: Property<Duration>

    @get:Input
    public abstract val shutdownTimeout: Property<Duration>

    /** Runs per variant. Ten by default; `-Pzavarnik.runs=N` overrides. */
    @get:Input
    public abstract val runs: Property<Int>

    /** `build/reports/zavarnik/aotReport.md`. */
    @get:OutputFile
    public abstract val reportFile: RegularFileProperty

    @TaskAction
    public fun report() {
        if (!readyUrl.isPresent) {
            throw GradleException(
                "zavarnik: aotReport measures time to the first 200 on `training { readyWhen.url(…) }`, which is not set.",
            )
        }
        val cache = cacheFile.get().asFile
        if (!cache.isFile) throw GradleException("zavarnik: no ${cache.name} to measure — run aotTrain first.")
        val cold = measure(listOf("-XX:AOTMode=off"))
        val cached = measure(emptyList())
        val javaVersion = javaLauncher.get().metadata.javaRuntimeVersion
        val table =
            buildString {
                appendLine("# aotReport — ${scriptName.get()}")
                appendLine()
                appendLine("Time to the first `200` on `${readyUrl.get()}`, measured from outside the process,")
                appendLine(
                    "${runs.get()} runs per variant, sorted. JDK $javaVersion, cache ${cache.length() / KIB} KiB.",
                )
                appendLine()
                appendLine("| Variant | Median, ms | Runs, ms |")
                appendLine("|---|---|---|")
                appendLine("| without the cache (`-XX:AOTMode=off`) | ${median(cold)} | ${cold.joinToString(" ")} |")
                appendLine("| with ${cache.name} | ${median(cached)} | ${cached.joinToString(" ")} |")
            }
        reportFile
            .get()
            .asFile
            .apply { parentFile.mkdirs() }
            .writeText(table)
        logger.lifecycle(
            "zavarnik: readiness median ${median(
                cold,
            )} ms cold, ${median(cached)} ms with ${cache.name} — ${reportFile.get().asFile}",
        )
    }

    private fun measure(javaOpts: List<String>): List<Long> {
        val script = File(installDir.get().asFile, "bin/${scriptName.get()}")
        val log = File(temporaryDir, "aotReport.log")
        return List(runs.get()) {
            val run =
                StartScriptRun(
                    script,
                    javaLauncher
                        .get()
                        .metadata.installationPath.asFile,
                    javaOpts,
                    log,
                )
            run.start()
            val millis = run.awaitReady(readyUrl.get(), readyTimeout.get())
            run.stop(shutdownTimeout.get())
            millis
        }.sorted()
    }

    private fun median(sorted: List<Long>): Long =
        if (sorted.size % 2 ==
            1
        ) {
            sorted[sorted.size / 2]
        } else {
            (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2
        }

    private companion object {
        const val KIB = 1024
    }
}

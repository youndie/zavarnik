package io.github.youndie.zavarnik.runner

import java.io.File
import kotlin.system.exitProcess

/**
 * `java -cp <runner jar> io.github.youndie.zavarnik.runner.Main train|verify|checkpoint|restore-verify [<dir>] [--out <dir>]`
 *
 * The same training and verification the Gradle tasks do, on a bare JRE: inside the runtime
 * stage of a container image, where there is no Gradle and, in Temurin's images, no curl. `<dir>`
 * is the installation — a distribution (`bin/`, `lib/`) or a Jib image's `/app` — and defaults to
 * the directory this jar sits two levels under (`<dir>/lib/zavarnik-runner.jar`,
 * `/app/zavarnik/zavarnik-runner.jar`). The JDK is the one running the runner, which is the
 * point: the cache must be trained by the JVM that will use it.
 *
 * `--out` is for a container started with a host directory mounted: the logs go there, and so
 * does the cache a training run writes, so that the host can lay it over the image as a layer.
 * Without it, everything is written beside the runner jar.
 *
 * `checkpoint` and `restore-verify` are the same two steps for CRaC: warm the application up and
 * snapshot it, then restore the snapshot and put the restored process through the workload again.
 * The snapshot is a directory rather than a file — `<out>/crac` — and it belongs to the image it
 * was taken in, which is why it goes to `--out` and comes back as a layer over that image.
 */
public object Main {
    @JvmStatic
    public fun main(args: Array<String>) {
        val command = args.getOrNull(0) ?: usage()
        if (command !in COMMANDS) usage()
        var dir: File? = null
        var out: File? = null
        var i = 1
        while (i < args.size) {
            when (args[i]) {
                "--out" -> out = args.getOrNull(++i)?.let(::File) ?: usage()
                else -> if (dir == null) dir = File(args[i]) else usage()
            }
            i++
        }
        val installDir = dir ?: ownInstallDir()
        try {
            run(command, installDir, out)
        } catch (failed: RunnerException) {
            System.err.println(failed.message)
            exitProcess(FAILURE)
        }
    }

    private fun run(
        command: String,
        dir: File,
        out: File?,
    ) {
        val javaHome = File(System.getProperty("java.home"))
        val jib = File(dir, "jib-classpath-file").isFile
        val configDir = File(dir, if (jib) Installation.JIB_RUNNER_DIR else "lib")
        val config = RunnerConfig.read(File(configDir, RunnerConfig.FILE_NAME))
        val installation =
            if (jib) {
                // A training run writes the cache where the host can reach it; a verification
                // reads the one the image carries.
                val runnerDir = if (command == TRAIN && out != null) out else File(dir, Installation.JIB_RUNNER_DIR)
                Installation.jib(dir, javaHome, config.launchJvmArgs, runnerDir)
            } else {
                Installation.distribution(dir, javaHome)
            }
        val log = File(out ?: installation.runnerDir, "zavarnik-$command.log")
        val cracImage = File(out ?: installation.runnerDir, config.cracImageDirName)
        when (command) {
            TRAIN -> Training(installation, config, log).run()
            VERIFY -> Verification(installation, config, log).run()
            CHECKPOINT -> Crac(installation, config, log, cracImage).checkpoint()
            else -> println("zavarnik: ${Crac(installation, config, log, cracImage).restoreVerify()}")
        }
    }

    private fun usage(): Nothing {
        System.err.println(
            "usage: java -cp <runner jar> io.github.youndie.zavarnik.runner.Main " +
                COMMANDS.joinToString("|") + " [<install dir>] [--out <dir>]",
        )
        exitProcess(USAGE)
    }

    private fun ownInstallDir(): File {
        val jar =
            File(
                Main::class.java.protectionDomain.codeSource.location
                    .toURI(),
            )
        return jar.parentFile.parentFile
    }

    private const val TRAIN = "train"
    private const val VERIFY = "verify"
    private const val CHECKPOINT = "checkpoint"
    private const val RESTORE_VERIFY = "restore-verify"
    private val COMMANDS = listOf(TRAIN, VERIFY, CHECKPOINT, RESTORE_VERIFY)
    private const val USAGE = 2
    private const val FAILURE = 1
}

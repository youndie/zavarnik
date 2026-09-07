package io.github.youndie.zavarnik.runner

import java.io.File
import kotlin.system.exitProcess

/**
 * `java -cp lib/zavarnik-runner.jar io.github.youndie.zavarnik.runner.Main train|verify [<install dir>]`
 *
 * The same training and verification the Gradle tasks do, on a bare JRE: inside the runtime
 * stage of a container image, where there is no Gradle and, in Temurin's images, no curl. The
 * install directory defaults to the one this jar sits in (`<dir>/lib/zavarnik-runner.jar`); the
 * JDK is the one running the runner, which is the point — the cache must be trained by the JVM
 * that will use it. Logs go to `lib/zavarnik-<command>.log`.
 */
public object Main {
    @JvmStatic
    public fun main(args: Array<String>) {
        val command = args.getOrNull(0)
        if (command != TRAIN && command != VERIFY) {
            System.err.println(
                "usage: java -cp lib/zavarnik-runner.jar io.github.youndie.zavarnik.runner.Main $TRAIN|$VERIFY [<install dir>]",
            )
            exitProcess(USAGE)
        }
        val dir = args.getOrNull(1)?.let(::File) ?: ownInstallDir()
        try {
            val installation = Installation(dir)
            val config = RunnerConfig.read(installation.config)
            val javaHome = File(System.getProperty("java.home"))
            val log = File(installation.lib, "zavarnik-$command.log")
            if (command == TRAIN) {
                Training(installation, config, javaHome, log).run()
            } else {
                Verification(installation, config, javaHome, log).run()
            }
        } catch (failed: RunnerException) {
            System.err.println(failed.message)
            exitProcess(FAILURE)
        }
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
    private const val USAGE = 2
    private const val FAILURE = 1
}

package io.github.youndie.zavarnik

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.distribution.DistributionContainer
import org.gradle.api.plugins.JavaApplication
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Tar
import org.gradle.api.tasks.bundling.Zip
import org.gradle.jvm.application.tasks.CreateStartScripts
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.language.base.plugins.LifecycleBasePlugin
import java.time.Duration

/**
 * Trains, verifies and ships a Project Leyden AOT cache for an application on the `application`
 * plugin.
 *
 * ```kotlin
 * plugins {
 *     application
 *     id("io.github.youndie.zavarnik")
 * }
 * ```
 *
 * `aotTrain` trains the cache through the start script, `aotVerify` fails the build when
 * production would not accept it (and runs on `check`), the start scripts pick the cache up when
 * it is there, and `distTar` ships it. `distZip` cannot: zip stores DOS timestamps in local time,
 * and the JVM checks jar mtimes against the cache. What the plugin refuses at configuration time,
 * and why, is in [ConfigurationChecks].
 */
public class ZavarnikPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val extension = target.extensions.create(EXTENSION_NAME, ZavarnikExtension::class.java)
        extension.applyDefaults()

        target.plugins.withId(ConfigurationChecks.APPLICATION_PLUGIN_ID) {
            target.wire(extension)
        }

        // The checks and the JVM arguments read the extension, so they wait for the build script to
        // finish with it. The `application` plugin may be applied after this one; `afterEvaluate`
        // sees the final state.
        target.afterEvaluate { project ->
            ConfigurationChecks.run(project, extension)
            project.applyJvmArgs(extension)
        }
    }

    private fun Project.wire(extension: ZavarnikExtension) {
        val application = extensions.getByType(JavaApplication::class.java)
        val java = extensions.getByType(JavaPluginExtension::class.java)
        val toolchains = extensions.getByType(JavaToolchainService::class.java)
        val launcher = toolchains.launcherFor(java.toolchain)
        val installDist = tasks.named(INSTALL_DIST_TASK, Sync::class.java)
        val installDir = installDist.map { layout.projectDirectory.dir(it.destinationDir.absolutePath) }
        val libFile = { name: String -> installDir.map { it.file("lib/$name") } }
        val scriptName = provider { application.applicationName }

        val train =
            tasks.register(TRAIN_TASK, AotTrainTask::class.java) { task ->
                task.group = GROUP
                task.description = "Trains the AOT cache by running the installed application through its start script."
                task.dependsOn(installDist)
                task.installDir.set(installDir)
                task.scriptName.set(scriptName)
                task.javaLauncher.set(launcher)
                task.cacheFileName.set(extension.cacheFileName)
                task.cacheFile.set(extension.cacheFileName.flatMap(libFile))
                task.manifestFile.set(extension.cacheFileName.flatMap { libFile("$it.jars") })
                task.logFile.set(layout.buildDirectory.file("zavarnik/aotTrain.log"))
                task.readyUrl.set(extension.training.readyWhen.url)
                task.workload.set(extension.training.workload.commands)
                task.exitAfter.set(extension.training.exitAfter)
                task.readyTimeout.set(extension.training.readyTimeout)
                task.shutdownTimeout.set(extension.training.shutdownTimeout)
            }

        val verify =
            tasks.register(VERIFY_TASK, AotVerifyTask::class.java) { task ->
                task.group = GROUP
                task.description =
                    "Fails the build unless the trained AOT cache is accepted and covers the application."
                // Explicit: the cache is deliberately not an input file (its absence is a message,
                // not a validation error), so nothing else ties the two tasks together.
                task.dependsOn(train)
                task.installDir.set(installDir)
                task.scriptName.set(scriptName)
                task.javaLauncher.set(launcher)
                task.cacheFile.set(train.flatMap { it.cacheFile })
                task.manifestFile.set(train.flatMap { it.manifestFile })
                task.logFile.set(layout.buildDirectory.file("zavarnik/aotVerify.log"))
                task.reportFile.set(layout.buildDirectory.file("zavarnik/aotVerify.txt"))
                task.readyUrl.set(extension.training.readyWhen.url)
                task.exitAfter.set(extension.training.exitAfter)
                task.readyTimeout.set(extension.training.readyTimeout)
                task.shutdownTimeout.set(extension.training.shutdownTimeout)
                task.minCachedShare.set(extension.verify.minCachedShare)
                task.extraJvmArgs.set(extension.verify.jvmArgs)
            }
        afterEvaluate {
            if (extension.verify.onCheck.get()) {
                tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME).configure { it.dependsOn(verify) }
            }
        }

        guardStartScripts(extension.cacheFileName)
        shipInArchives(train, application)
    }

    /**
     * The user's `jvmArgs` and the portability flags go into `applicationDefaultJvmArgs`, i.e. into
     * the script's `DEFAULT_JVM_OPTS`: one source for the training run, the verification run and
     * production, because the JVM compares the recorded command line with the runtime's.
     */
    private fun Project.applyJvmArgs(extension: ZavarnikExtension) {
        if (!plugins.hasPlugin(ConfigurationChecks.APPLICATION_PLUGIN_ID)) return
        val application = extensions.getByType(JavaApplication::class.java)
        val portability = if (extension.portability.get()) PORTABILITY_FLAGS else emptyList()
        application.applicationDefaultJvmArgs =
            application.applicationDefaultJvmArgs + extension.jvmArgs.get() + portability
    }

    private fun Project.guardStartScripts(cacheFileName: Provider<String>) {
        tasks.named(START_SCRIPTS_TASK, CreateStartScripts::class.java).configure { task ->
            task.doLast("zavarnikGuard") {
                val name = cacheFileName.get()
                task.unixScript.writeText(StartScriptGuard.unix(task.unixScript.readText(), name))
                task.windowsScript.writeText(StartScriptGuard.windows(task.windowsScript.readText(), name))
            }
        }
    }

    /**
     * The tar gets the cache and the manifest; the zip gets a warning. The archives depend on
     * `aotTrain` through its outputs — not through the distribution's shared content, which
     * `installDist` also copies and `aotTrain` already depends on.
     */
    private fun Project.shipInArchives(
        train: TaskProvider<AotTrainTask>,
        application: JavaApplication,
    ) {
        val distributions = extensions.getByType(DistributionContainer::class.java)
        val libInArchive =
            distributions.named(MAIN_DISTRIBUTION).flatMap { it.distributionBaseName }.map { base ->
                val version = version.toString()
                (if (version == UNSPECIFIED_VERSION) base else "$base-$version") + "/lib"
            }
        tasks.named(DIST_TAR_TASK, Tar::class.java).configure { tar ->
            tar.from(train.flatMap { it.cacheFile }) { spec -> spec.into(libInArchive) }
            tar.from(train.flatMap { it.manifestFile }) { spec -> spec.into(libInArchive) }
        }
        tasks.named(DIST_ZIP_TASK, Zip::class.java).configure { zip ->
            zip.doFirst("zavarnikZipWarning") {
                logger.warn(
                    "zavarnik: ${zip.name} ships no AOT cache. Zip stores DOS timestamps in local time and " +
                        "the JVM checks jar mtimes against the cache, so an unzipped distribution would " +
                        "reject it on another machine. Ship distTar or installDist (Docker COPY) instead; " +
                        "`${application.applicationName}` will run without the cache.",
                )
            }
        }
    }

    private fun ZavarnikExtension.applyDefaults() {
        portability.convention(true)
        cacheFileName.convention(DEFAULT_CACHE_FILE_NAME)
        training.readyTimeout.convention(Duration.ofMinutes(2))
        training.shutdownTimeout.convention(Duration.ofMinutes(5))
        verify.minCachedShare.convention(DEFAULT_MIN_CACHED_SHARE)
        verify.onCheck.convention(true)
    }

    public companion object {
        /** `zavarnik { }` in the build script. */
        public const val EXTENSION_NAME: String = "zavarnik"

        /** Where the cache lives inside the distribution: `lib/app.aot`. */
        public const val DEFAULT_CACHE_FILE_NAME: String = "app.aot"

        /** `aotTrain`. */
        public const val TRAIN_TASK: String = "aotTrain"

        /** `aotVerify`. */
        public const val VERIFY_TASK: String = "aotVerify"

        /** The task group the plugin's tasks show up under. */
        public const val GROUP: String = "zavarnik"

        /**
         * Off: the adapter code the JVM caches ergonomically for the training CPU, which crashes
         * with `SIGILL` on a narrower one. `UnlockDiagnosticVMOptions` has to come first.
         */
        public val PORTABILITY_FLAGS: List<String> = listOf("-XX:+UnlockDiagnosticVMOptions", "-XX:-AOTAdapterCaching")
        private const val INSTALL_DIST_TASK = "installDist"
        private const val START_SCRIPTS_TASK = "startScripts"
        private const val DIST_TAR_TASK = "distTar"
        private const val DIST_ZIP_TASK = "distZip"
        private const val MAIN_DISTRIBUTION = "main"
        private const val UNSPECIFIED_VERSION = "unspecified"
        private const val DEFAULT_MIN_CACHED_SHARE = 0.9
    }
}

package io.github.youndie.zavarnik

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaApplication
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.Sync
import org.gradle.jvm.toolchain.JavaToolchainService
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
 * The plugin adds `aotTrain`, `aotVerify` and `aotReport`, and wires the cache into the start
 * scripts and the distribution. What it refuses at configuration time, and why, is in
 * [ConfigurationChecks].
 */
public class ZavarnikPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val extension = target.extensions.create(EXTENSION_NAME, ZavarnikExtension::class.java)
        extension.applyDefaults()

        target.plugins.withId(ConfigurationChecks.APPLICATION_PLUGIN_ID) {
            target.registerTrain(extension)
        }

        // The checks read the extension, so they run once the build script has finished with it. The
        // `application` plugin may be applied after this one; `afterEvaluate` sees the final state.
        target.afterEvaluate { project -> ConfigurationChecks.run(project, extension) }
    }

    private fun Project.registerTrain(extension: ZavarnikExtension) {
        val application = extensions.getByType(JavaApplication::class.java)
        val java = extensions.getByType(JavaPluginExtension::class.java)
        val toolchains = extensions.getByType(JavaToolchainService::class.java)
        val installDist = tasks.named(INSTALL_DIST_TASK, Sync::class.java)
        val installDir = installDist.map { layout.projectDirectory.dir(it.destinationDir.absolutePath) }
        val libFile = { name: String -> installDir.map { it.file("lib/$name") } }

        tasks.register(TRAIN_TASK, AotTrainTask::class.java) { task ->
            task.group = GROUP
            task.description = "Trains the AOT cache by running the installed application through its start script."
            task.dependsOn(installDist)
            task.installDir.set(installDir)
            task.scriptName.set(provider { application.applicationName })
            task.javaLauncher.set(toolchains.launcherFor(java.toolchain))
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

        /** The task group the plugin's tasks show up under. */
        public const val GROUP: String = "zavarnik"
        private const val INSTALL_DIST_TASK = "installDist"
        private const val DEFAULT_MIN_CACHED_SHARE = 0.9
    }
}

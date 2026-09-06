package io.github.youndie.zavarnik

import org.gradle.api.Plugin
import org.gradle.api.Project
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
 * The plugin adds three tasks — `aotTrain`, `aotVerify`, `aotReport` — and wires the cache into the
 * start scripts and the distribution. What it refuses at configuration time, and why, is in
 * [ConfigurationChecks].
 */
public class ZavarnikPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val extension = target.extensions.create(EXTENSION_NAME, ZavarnikExtension::class.java)
        extension.applyDefaults()

        // The checks read the extension, so they run once the build script has finished with it. The
        // `application` plugin may be applied after this one; `afterEvaluate` sees the final state.
        target.afterEvaluate { project -> ConfigurationChecks.run(project, extension) }
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
        private const val DEFAULT_MIN_CACHED_SHARE = 0.9
    }
}

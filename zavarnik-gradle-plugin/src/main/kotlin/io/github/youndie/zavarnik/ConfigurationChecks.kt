package io.github.youndie.zavarnik

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaToolchainService

/**
 * What can be refused before anything runs.
 *
 * Half of the conditions under which the JVM will reject a cache are visible at configuration time
 * (`docs/research/research-architecture.md`, D8). Finding them in `aotVerify`, after a training
 * run, is learning after the fact what was knowable before it.
 */
internal object ConfigurationChecks {
    const val APPLICATION_PLUGIN_ID: String = "org.gradle.application"

    fun run(
        project: Project,
        extension: ZavarnikExtension,
    ) {
        if (!project.plugins.hasPlugin(APPLICATION_PLUGIN_ID)) {
            throw GradleException(
                "zavarnik needs the `application` plugin in ${project.displayName}: the AOT cache is " +
                    "trained through the start script over the `lib/*.jar` layout that plugin " +
                    "produces. A classpath of directories, which is what `run` uses, yields no cache at all.",
            )
        }
        val jdk = project.toolchainVersion()
        if (!jdk.supportsOneStepWorkflow) {
            throw GradleException(
                "zavarnik needs a JDK 25 or newer toolchain in ${project.displayName}, found $jdk. " +
                    "The one-step AOT workflow (-XX:AOTCacheOutput) exists from JDK 25 (JEP 514).",
            )
        }
        val args = extension.jvmArgs.get()
        if (args.any { it == ZGC_FLAG } && !jdk.supportsZgcWithAotCache) {
            // Measured, not read: a cache trained under ZGC on 25.0.4 is used under ZGC — 767 classes
            // from the cache on the hello-world stand against 894 under G1, the difference being the
            // archived heap objects ZGC cannot map before JEP 516. The flag lives in DEFAULT_JVM_OPTS
            // for both runs, so the symmetry the JVM needs is what the plugin produces anyway.
            project.logger.warn(
                "zavarnik: `$ZGC_FLAG` with a JDK $jdk toolchain — the cache will be used, but without " +
                    "archived heap objects, which JDK 26 (JEP 516) adds under ZGC; expect a smaller gain.",
            )
        }
        if (jdk.skipsJarValidation) {
            project.logger.warn(
                "zavarnik: JDK $jdk carries JDK-8377932 — the JVM will not notice a changed jar and will " +
                    "use a stale AOT cache silently. aotVerify compares the jars itself; upgrade to " +
                    "25.0.4 / 26.0.2 or newer for the JVM to do it too.",
            )
        }
    }

    /**
     * The exact runtime version of the toolchain's JDK, from the launcher's metadata. Falls back to
     * the JVM running Gradle when no toolchain is configured, which is what Gradle itself does.
     */
    private fun Project.toolchainVersion(): JdkVersion {
        val java = extensions.getByType(JavaPluginExtension::class.java)
        val toolchains = extensions.getByType(JavaToolchainService::class.java)
        val runtime =
            if (java.toolchain.languageVersion.isPresent) {
                toolchains
                    .launcherFor(java.toolchain)
                    .get()
                    .metadata.javaRuntimeVersion
            } else {
                System.getProperty("java.runtime.version")
            }
        return JdkVersion.parse(runtime)
            ?: throw GradleException("zavarnik: cannot read a JDK version out of \"$runtime\".")
    }

    private const val ZGC_FLAG = "-XX:+UseZGC"
}

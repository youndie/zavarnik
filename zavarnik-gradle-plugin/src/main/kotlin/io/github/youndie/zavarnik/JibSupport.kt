package io.github.youndie.zavarnik

import io.github.youndie.zavarnik.runner.Installation
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Project
import java.io.File

/**
 * The Jib mode, wired when `com.google.cloud.tools.jib` is applied — which is also what the Ktor
 * plugin's `ktor { docker { } }` applies.
 *
 * Jib builds an image without running a container, so the cache cannot be trained "inside the
 * image" during the build. The recipe is two Jib builds: `jibAotTrain` has `jibDockerBuild`
 * produce the image, runs it with the runner (`docker run`), and keeps the cache under
 * `build/zavarnik/jib/`; from then on that directory is one more of Jib's `extraDirectories` and
 * the entrypoint carries `-XX:AOTCache`, so the next `jibDockerBuild` or `jib` is the image with
 * the cache — one layer more than the image without it, the jars and their mtimes the same.
 * `jibAotVerify` runs the runner's verification in that image.
 *
 * Refused at configuration time: the `exploded` layout, Jib's default, whose classpath has two
 * directories on it — the JVM writes no cache for that (research E4).
 *
 * Jib's extension is driven by name and reflection rather than by its types: the two plugins do
 * not share a class loader in every build (TestKit's injected classpath is one case, a plugin
 * applied from a parent project another), and a hard reference to `JibExtension` would then fail
 * with `NoClassDefFoundError` before this plugin could say anything useful.
 */
internal object JibSupport {
    const val JIB_PLUGIN_ID: String = "com.google.cloud.tools.jib"
    const val TRAIN_TASK: String = "jibAotTrain"
    const val VERIFY_TASK: String = "jibAotVerify"
    private const val JIB_EXTENSION = "jib"
    private const val JIB_DOCKER_BUILD_TASK = "jibDockerBuild"
    private const val PACKAGED = "packaged"
    private const val DEFAULT_APP_ROOT = "/app"

    fun wire(
        project: Project,
        extension: ZavarnikExtension,
    ) {
        val jib = Reflected(project.extensions.getByName(JIB_EXTENSION))
        val cacheDir = project.layout.buildDirectory.dir("zavarnik/jib")
        val runnerFiles = project.tasks.named(ZavarnikPlugin.RUNNER_FILES_TASK, RunnerFilesTask::class.java)

        val train =
            project.tasks.register(TRAIN_TASK, JibAotTrainTask::class.java) { task ->
                task.group = ZavarnikPlugin.GROUP
                task.description =
                    "Builds the Jib image, trains the AOT cache inside a container of it, keeps the cache for the next build."
                task.dependsOn(JIB_DOCKER_BUILD_TASK)
                task.imageJson.set(project.layout.buildDirectory.file("jib-image.json"))
                task.cacheDir.set(cacheDir)
                task.logFile.set(project.layout.buildDirectory.file("zavarnik/jibAotTrain.log"))
            }
        project.tasks.register(VERIFY_TASK, JibAotVerifyTask::class.java) { task ->
            task.group = ZavarnikPlugin.GROUP
            task.description =
                "Builds the Jib image with the trained cache and verifies the cache inside a container of it."
            task.dependsOn(JIB_DOCKER_BUILD_TASK)
            task.mustRunAfter(train)
            task.imageJson.set(project.layout.buildDirectory.file("jib-image.json"))
            task.cacheDir.set(cacheDir)
            task.logFile.set(project.layout.buildDirectory.file("zavarnik/jibAotVerify.log"))
            task.reportFile.set(project.layout.buildDirectory.file("zavarnik/jibAotVerify.txt"))
        }
        for (name in listOf(JIB_DOCKER_BUILD_TASK, "jib", "jibBuildTar")) {
            project.tasks.named(name).configure { it.dependsOn(runnerFiles) }
        }

        project.afterEvaluate {
            val mode = jib.get("getContainerizingMode") as String
            if (mode != PACKAGED) {
                throw GradleException(
                    "zavarnik: Jib's `containerizingMode` is `$mode` in ${project.displayName}, whose classpath " +
                        "has `/app/resources` and `/app/classes` on it — directories, for which the JVM writes " +
                        "no AOT cache at all. Set `jib { containerizingMode = \"packaged\" }`.",
                )
            }
            val container = Reflected(jib.get("getContainer")!!)
            val appRoot = (container.get("getAppRoot") as? String).orEmpty().ifEmpty { DEFAULT_APP_ROOT }
            val runnerDir = "$appRoot/${Installation.JIB_RUNNER_DIR}"
            val portability = if (extension.portability.get()) ZavarnikPlugin.PORTABILITY_FLAGS else emptyList()

            // What the entrypoint starts with in production, and therefore what the runner has to
            // put on its own command line: Jib's flags, the user's, the portability flags.
            @Suppress("UNCHECKED_CAST")
            val jibFlags = (container.get("getJvmFlags") as? List<String>).orEmpty()
            val launchFlags = (jibFlags + extension.jvmArgs.get() + portability).distinct()
            runnerFiles.configure { it.launchJvmArgs.set(launchFlags) }
            val cache = File(cacheDir.get().asFile, extension.cacheFileName.get())
            val cacheFlag = if (cache.isFile) listOf("-XX:AOTCache=$runnerDir/${cache.name}") else emptyList()
            container.call("setJvmFlags", List::class.java, launchFlags + cacheFlag)

            val runnerFilesDir =
                runnerFiles
                    .get()
                    .outputDir
                    .get()
                    .asFile
            val extraDirectories = Reflected(jib.get("getExtraDirectories")!!)
            extraDirectories.call(
                "paths",
                Action::class.java,
                Action<Any> { spec ->
                    val paths = Reflected(spec)
                    paths.call(
                        "path",
                        Action::class.java,
                        Action<Any> { Reflected(it).into(runnerFilesDir, runnerDir) },
                    )
                    if (cache.isFile) {
                        paths.call(
                            "path",
                            Action::class.java,
                            Action<Any> { Reflected(it).into(cacheDir.get().asFile, runnerDir) },
                        )
                    }
                },
            )
            train.configure {
                it.appRoot.set(appRoot)
                it.cacheFileName.set(extension.cacheFileName)
            }
            project.tasks.named(VERIFY_TASK, JibAotVerifyTask::class.java).configure {
                it.appRoot.set(appRoot)
                it.cacheFileName.set(extension.cacheFileName)
                it.cacheInImage.set(cache.isFile)
            }
        }
    }

    /** A Jib parameters object addressed by method name, for the handful of calls the mode needs. */
    private class Reflected(
        private val target: Any,
    ) {
        fun get(getter: String): Any? = target.javaClass.getMethod(getter).invoke(target)

        fun call(
            method: String,
            parameterType: Class<*>,
            argument: Any,
        ) {
            target.javaClass.getMethod(method, parameterType).invoke(target, argument)
        }

        /** `ExtraDirectoryParameters`: `setFrom(Object)`, `setInto(String)`. */
        fun into(
            from: File,
            into: String,
        ) {
            call("setFrom", Any::class.java, from)
            call("setInto", String::class.java, into)
        }
    }
}

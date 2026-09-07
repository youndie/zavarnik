package io.github.youndie.zavarnik

import io.github.youndie.zavarnik.runner.Installation
import io.github.youndie.zavarnik.runner.Json
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

/**
 * Trains the cache inside a container of the image `jibDockerBuild` just built: the runner the
 * image carries in `/app/zavarnik/` starts the application the way the entrypoint does, with
 * `-XX:AOTCacheOutput` pointed at a mounted directory, so the cache lands in `build/zavarnik/jib/`
 * on the host — where the next Jib build picks it up as a layer.
 *
 * The container runs as the host user so that what it writes into the mount belongs to the host
 * user; the image's own files are read, not written.
 */
@DisableCachingByDefault(because = "runs the image; the answer depends on the JDK build inside it")
public abstract class JibAotTrainTask : DefaultTask() {
    /** Jib's record of what it built: `build/jib-image.json`, with the image name and its tags. */
    @get:Internal
    public abstract val imageJson: RegularFileProperty

    @get:Input
    public abstract val appRoot: Property<String>

    @get:Input
    public abstract val cacheFileName: Property<String>

    /** `build/zavarnik/jib/`: the cache and the manifest, for the next Jib build. */
    @get:OutputDirectory
    public abstract val cacheDir: DirectoryProperty

    @get:OutputFile
    public abstract val logFile: RegularFileProperty

    @TaskAction
    public fun train() {
        val image = JibImage.reference(imageJson.get().asFile)
        val out = cacheDir.get().asFile
        out.deleteRecursively()
        out.mkdirs()
        val log = logFile.get().asFile
        log.delete()
        DockerCommand.run(
            JibImage.runnerCommand(image, appRoot.get(), out, "train"),
            log,
            "training inside $image",
        )
        val cache = File(out, cacheFileName.get())
        if (!cache.isFile) {
            throw GradleException(
                "zavarnik: the training run in $image left no ${cache.name} behind.\n${DockerCommand.tail(log)}",
            )
        }
        logger.lifecycle(
            "zavarnik: ${cache.name} is ${cache.length() / KIB} KiB, trained inside $image. " +
                "The next jibDockerBuild or jib builds the image with it; jibAotVerify then checks it there.",
        )
    }

    private companion object {
        const val KIB = 1024
    }
}

/** The image `jibDockerBuild` produced, and the command that runs the runner inside it. */
internal object JibImage {
    fun reference(imageJson: File): String {
        if (!imageJson.isFile) throw GradleException("zavarnik: no ${imageJson.path} — did jibDockerBuild run?")
        val json = Json.parse(imageJson.readText())
        val image = Json.extract(json, "image")
        val tag = Json.extract(json, "tags.0")
        return "$image:$tag"
    }

    /**
     * `docker run --rm --user <uid:gid> -v <out>:/zavarnik-out --entrypoint java <image>
     *   -cp <appRoot>/zavarnik/zavarnik-runner.jar io.github.youndie.zavarnik.runner.Main <command> <appRoot> --out /zavarnik-out`
     */
    fun runnerCommand(
        image: String,
        appRoot: String,
        out: File,
        command: String,
    ): List<String> =
        listOf(
            "run",
            "--rm",
            "--user",
            DockerCommand.hostUser(),
            "-v",
            "${out.absolutePath}:$MOUNT",
            "--entrypoint",
            "java",
            image,
            "-cp",
            "$appRoot/${Installation.JIB_RUNNER_DIR}/${RunnerFilesTask.RUNNER_JAR_NAME}",
            RUNNER_MAIN,
            command,
            appRoot,
            "--out",
            MOUNT,
        )

    private const val MOUNT = "/zavarnik-out"
    private const val RUNNER_MAIN = "io.github.youndie.zavarnik.runner.Main"
}

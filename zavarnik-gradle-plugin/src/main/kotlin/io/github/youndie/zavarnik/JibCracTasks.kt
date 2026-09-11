package io.github.youndie.zavarnik

import io.github.youndie.zavarnik.runner.Installation
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

/**
 * Takes a CRaC checkpoint inside a container of the image `jibDockerBuild` just built: the runner
 * the image carries starts the application the way the entrypoint does, warms it through the
 * workload and snapshots it into a mounted directory, so the snapshot lands under
 * `build/zavarnik/jib-crac/` on the host — where the next Jib build lays it over the very image it
 * was taken in.
 *
 * The snapshot is bound to that image down to the build id of every file the process had mapped,
 * so the layer goes over *this* image and no other. Its path inside the image is not the path it
 * was taken at, and that is fine — measured in `experiments/crac-ktor/image-layer.sh`. What is not
 * fine, and is why the policy file lives in the image rather than in the mount, is the path the
 * JVM recorded for `jdk.crac.resource-policies`: a restore reads it again.
 */
@DisableCachingByDefault(because = "runs the image; the snapshot belongs to the JDK build inside it")
public abstract class JibCracCheckpointTask : DefaultTask() {
    /** Jib's record of what it built: `build/jib-image.json`, with the image name and its tags. */
    @get:Internal
    public abstract val imageJson: RegularFileProperty

    @get:Input
    public abstract val appRoot: Property<String>

    /** Extra `docker run` arguments — the stand's network and environment ([JibSpec.dockerRunArgs]). */
    @get:Input
    public abstract val dockerRunArgs: ListProperty<String>

    /** `build/zavarnik/jib-crac/`: the snapshot directory, for the next Jib build. */
    @get:OutputDirectory
    public abstract val snapshotDir: DirectoryProperty

    @get:OutputFile
    public abstract val logFile: RegularFileProperty

    @TaskAction
    public fun checkpoint() {
        val image = JibImage.reference(imageJson.get().asFile)
        val out = snapshotDir.get().asFile
        out.deleteRecursively()
        out.mkdirs()
        val log = logFile.get().asFile
        log.delete()
        DockerCommand.run(
            JibImage.runnerCommand(image, appRoot.get(), out, CHECKPOINT, dockerRunArgs.get()),
            log,
            "taking a checkpoint inside $image",
        )
        val snapshot = File(out, SNAPSHOT_DIR)
        val files = snapshot.listFiles().orEmpty().filter { it.isFile }
        if (files.isEmpty()) {
            throw GradleException(
                "zavarnik: the run in $image left no snapshot in ${snapshot.name}.\n${DockerCommand.tail(log)}",
            )
        }
        logger.lifecycle(
            "zavarnik: the snapshot is ${files.sumOf { it.length() } / KIB} KiB, taken inside $image. " +
                "The next jibDockerBuild or jib builds the image with it as a layer and with the restore as " +
                "its entrypoint; ${JibSupport.CRAC_VERIFY_TASK} then restores it there.",
        )
    }

    internal companion object {
        const val CHECKPOINT: String = "checkpoint"
        const val SNAPSHOT_DIR: String = "crac"
        private const val KIB = 1024
    }
}

/**
 * Restores the snapshot inside a container of the image that carries it, and puts the restored
 * process through the workload.
 *
 * The workload is the check and the start is not: a restore keeps the sockets the policy told it
 * to ignore, pointing at `/dev/null`, so a process that came up proves nothing about the pool
 * behind it. Only a request that reaches the far side does.
 *
 * Refuses when the image built in this invocation carries no snapshot — Jib reads its
 * configuration once per build, so the first checkpoint and the first verification are two
 * invocations, exactly as in the AOT mode.
 */
@DisableCachingByDefault(because = "runs the image; the answer depends on what is inside it")
public abstract class JibCracVerifyTask : DefaultTask() {
    @get:Internal
    public abstract val imageJson: RegularFileProperty

    @get:Input
    public abstract val appRoot: Property<String>

    @get:Input
    public abstract val dockerRunArgs: ListProperty<String>

    /** Whether the snapshot existed when Jib was configured — i.e. whether this image carries it. */
    @get:Input
    public abstract val snapshotInImage: Property<Boolean>

    @get:Internal
    public abstract val snapshotDir: DirectoryProperty

    @get:OutputFile
    public abstract val logFile: RegularFileProperty

    @TaskAction
    public fun verify() {
        val snapshot = File(snapshotDir.get().asFile, JibCracCheckpointTask.SNAPSHOT_DIR)
        if (!snapshotInImage.get() || snapshot.listFiles().orEmpty().none { it.isFile }) {
            throw GradleException(
                "zavarnik: the image built in this invocation carries no CRaC snapshot, so there is nothing to " +
                    "restore in it. Jib reads its configuration once per build: run " +
                    "`${JibSupport.CRAC_CHECKPOINT_TASK}` first, then `${JibSupport.CRAC_VERIFY_TASK}` in a " +
                    "separate invocation.",
            )
        }
        val image = JibImage.reference(imageJson.get().asFile)
        val log = logFile.get().asFile
        log.delete()
        // --image, because the snapshot to restore is the one the image carries, while the log
        // still has to go somewhere the container may write.
        DockerCommand.run(
            JibImage.runnerCommand(image, appRoot.get(), log.parentFile, RESTORE_VERIFY, dockerRunArgs.get()) +
                listOf(
                    "--image",
                    "${appRoot.get()}/${Installation.JIB_RUNNER_DIR}/${JibCracCheckpointTask.SNAPSHOT_DIR}",
                ),
            log,
            "restoring inside $image",
        )
        logger.lifecycle("zavarnik: $image restored and served the workload.")
    }

    private companion object {
        const val RESTORE_VERIFY = "restore-verify"
    }
}

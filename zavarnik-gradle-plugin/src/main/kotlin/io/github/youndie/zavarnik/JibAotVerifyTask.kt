package io.github.youndie.zavarnik

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

/**
 * Runs the runner's verification inside a container of the image `jibDockerBuild` just built —
 * the image that carries the cache as a layer and `-XX:AOTCache` in its entrypoint. The three
 * checks are [io.github.youndie.zavarnik.runner.Verification]'s; the report is the runner's
 * summary line.
 *
 * Refuses to run when the image built in this invocation carries no cache: Jib reads its
 * configuration once per build, so the first `jibAotTrain` and the first verification are two
 * invocations. From then on the cache is there at configuration time and `jibAotTrain jibAotVerify`
 * in one invocation verifies the image built with the previous cache — which is what production
 * would have run, and a changed jar fails it the way it should.
 */
@DisableCachingByDefault(because = "runs the image; the answer depends on the JDK build inside it")
public abstract class JibAotVerifyTask : DefaultTask() {
    @get:Internal
    public abstract val imageJson: RegularFileProperty

    @get:Input
    public abstract val appRoot: Property<String>

    @get:Input
    public abstract val cacheFileName: Property<String>

    /** Extra `docker run` arguments — the stand's network and environment ([JibSpec.dockerRunArgs]). */
    @get:Input
    public abstract val dockerRunArgs: ListProperty<String>

    /** Whether the cache existed when Jib was configured — i.e. whether the image built in this invocation carries it. */
    @get:Input
    public abstract val cacheInImage: Property<Boolean>

    @get:Internal
    public abstract val cacheDir: DirectoryProperty

    @get:OutputFile
    public abstract val logFile: RegularFileProperty

    @get:OutputFile
    public abstract val reportFile: RegularFileProperty

    @TaskAction
    public fun verify() {
        val cache = File(cacheDir.get().asFile, cacheFileName.get())
        if (!cache.isFile || !cacheInImage.get()) {
            throw GradleException(
                "zavarnik: the image built in this invocation carries no ${cache.name}. " +
                    "Run `${JibSupport.TRAIN_TASK}` first, then `${JibSupport.VERIFY_TASK}` in a separate " +
                    "invocation: Jib reads its configuration once per build, and the cache becomes a layer " +
                    "of the next one.",
            )
        }
        val image = JibImage.reference(imageJson.get().asFile)
        val out = File(temporaryDir, "verify")
        out.deleteRecursively()
        out.mkdirs()
        val log = logFile.get().asFile
        log.delete()
        DockerCommand.run(
            JibImage.runnerCommand(image, appRoot.get(), out, "verify", dockerRunArgs.get()),
            log,
            "verifying inside $image",
        )
        val summary =
            log.readLines().lastOrNull { it.startsWith("zavarnik: ") && "came from" in it }
                ?: throw GradleException(
                    "zavarnik: the verification in $image printed no summary.\n${DockerCommand.tail(log)}",
                )
        reportFile.get().asFile.writeText(summary.removePrefix("zavarnik: ") + "\n")
        logger.lifecycle("$summary — inside $image")
    }
}

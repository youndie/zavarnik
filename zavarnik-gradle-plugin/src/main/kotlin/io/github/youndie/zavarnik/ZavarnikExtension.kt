package io.github.youndie.zavarnik

import io.github.youndie.zavarnik.runner.WorkloadStep
import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import java.time.Duration
import javax.inject.Inject

/**
 * Configuration for the `io.github.youndie.zavarnik` plugin, available as `zavarnik { }` in a
 * consumer's build script.
 *
 * ```kotlin
 * zavarnik {
 *     jvmArgs("-XX:+UseSerialGC")
 *     training {
 *         readyWhen.url("http://localhost:8080/health")
 *         workload { exec("curl", "-s", "http://localhost:8080/api/warm") }
 *     }
 * }
 * ```
 *
 * Everything not set here has a default that follows from the research this plugin was built on
 * (`docs/research/research-architecture.md` in the repository): the cache lives at
 * `lib/app.aot` next to the jars, the training run is stopped with `SIGTERM`, and the cache is
 * built without the CPU-specific adapter code so that a cache trained on CI runs anywhere.
 */
public abstract class ZavarnikExtension
    @Inject
    constructor(
        objects: ObjectFactory,
    ) {
        /**
         * JVM arguments the application runs with — in the training run *and* in production, through
         * the start script's `DEFAULT_JVM_OPTS`. They have to be the same: the JVM rejects a cache
         * whose recorded module options differ from the runtime's, and on JDK 25 a cache trained
         * under one GC family is not usable under ZGC.
         */
        public abstract val jvmArgs: ListProperty<String>

        /** Adds to [jvmArgs]. */
        public fun jvmArgs(vararg args: String) {
            jvmArgs.addAll(args.toList())
        }

        /**
         * Whether the cache must run on a CPU other than the one it was trained on. `true` by default:
         * the JVM enables `AOTAdapterCaching` ergonomically whenever a cache is in use, the cached
         * adapters are generated for the training machine's instruction set, and a cache trained on a
         * CI runner with AVX-512 crashes with `SIGILL` on a narrower CPU. Set to `false` only when
         * training and production are the same machine.
         */
        public abstract val portability: Property<Boolean>

        /** File name of the cache inside the distribution's `lib/` directory. `app.aot` by default. */
        public abstract val cacheFileName: Property<String>

        /** The training run: how the plugin knows the application is up, what it does to it, how it stops it. */
        public val training: TrainingSpec = objects.newInstance(TrainingSpec::class.java)

        /** Configures [training]. */
        public fun training(action: Action<in TrainingSpec>) {
            action.execute(training)
        }

        /** What `aotVerify` demands before it lets the build pass. */
        public val verify: VerifySpec = objects.newInstance(VerifySpec::class.java)

        /** Configures [verify]. */
        public fun verify(action: Action<in VerifySpec>) {
            action.execute(verify)
        }

        /** The Jib mode's knobs — see [JibSpec]. Ignored unless Jib is applied. */

        /** `crac { }` — what a checkpoint of this application has to be told about. */
        public val crac: CracSpec = objects.newInstance(CracSpec::class.java)

        /** `crac { ignoreRemotePort(5432) }`. */
        public fun crac(action: Action<in CracSpec>) {
            action.execute(crac)
        }

        public val jib: JibSpec = objects.newInstance(JibSpec::class.java)

        /** Configures [jib]. */
        public fun jib(action: Action<in JibSpec>) {
            action.execute(jib)
        }
    }

/** The training run. Either [readyWhen] plus [workload], or [exitAfter] for an application without a port. */
public abstract class TrainingSpec
    @Inject
    constructor(
        objects: ObjectFactory,
    ) {
        /** How the plugin decides the application is ready to take the workload. */
        public val readyWhen: ReadinessSpec = objects.newInstance(ReadinessSpec::class.java)

        /** Configures [readyWhen]. */
        public fun readyWhen(action: Action<in ReadinessSpec>) {
            action.execute(readyWhen)
        }

        /** Commands run against the ready application before it is stopped; what they load is what gets cached. */
        public val workload: WorkloadSpec = objects.newInstance(WorkloadSpec::class.java)

        /** Configures [workload]. */
        public fun workload(action: Action<in WorkloadSpec>) {
            action.execute(workload)
        }

        /**
         * Stop the application this long after it started, instead of waiting for [readyWhen]. For
         * applications without an HTTP surface. Unset by default.
         */
        public abstract val exitAfter: Property<Duration>

        /** How long to wait for [readyWhen] before the training run is declared failed. Two minutes by default. */
        public abstract val readyTimeout: Property<Duration>

        /**
         * Whether `distTar` — and with it `assemble` and `build` — trains the cache. `true` by
         * default: the tar ships what `aotTrain` made. `false` for an application that cannot start
         * on the build machine (no database, no broker): `assemble` then packs no cache, `distTar`
         * still carries one when a training run has left it in `installDist`, and the training
         * happens where the application can run — on a stand, inside the image, through the runner.
         */
        public abstract val onAssemble: Property<Boolean>

        /**
         * How long to wait, after `SIGTERM`, for the launcher to exit and the cache to be assembled.
         * The one-step workflow starts a second JVM to write the cache after the first one has exited,
         * so this covers both. Five minutes by default. When it expires the process is killed, which
         * loses the cache, and the task fails rather than pretending.
         */
        public abstract val shutdownTimeout: Property<Duration>
    }

/** Readiness: today an HTTP URL that has to answer `200`. */
public abstract class ReadinessSpec {
    /** The URL polled until it answers `200`. */
    public abstract val url: Property<String>

    /** Sets [url]. */
    public fun url(value: String) {
        url.set(value)
    }
}

/**
 * The workload: HTTP requests and external commands, run in the order they were declared, each
 * checked — a non-2xx answer or a non-zero exit fails the training run.
 *
 * Prefer [get] and [post] to `exec("curl", …)`: they need nothing installed, which is what makes
 * the training run work inside a `docker build` stage or on a runner without curl.
 */
public abstract class WorkloadSpec {
    /** The steps, in order. */
    public abstract val steps: ListProperty<WorkloadStep>

    /** Appends an HTTP `GET`. */
    public fun get(url: String) {
        steps.add(WorkloadStep.http("GET", url, null, null))
    }

    /** Appends an HTTP `GET` with headers and captures — see [RequestSpec]. */
    public fun get(
        url: String,
        configure: Action<RequestSpec>,
    ) {
        val spec = RequestSpec().also(configure::execute)
        steps.add(WorkloadStep.http("GET", url, null, null, spec.headers, spec.captures))
    }

    /** Appends an HTTP `POST` with a body. */
    public fun post(
        url: String,
        contentType: String,
        body: String,
    ) {
        steps.add(WorkloadStep.http("POST", url, contentType, body))
    }

    /** Appends an HTTP `POST` with a body, headers and captures — see [RequestSpec]. */
    public fun post(
        url: String,
        contentType: String,
        body: String,
        configure: Action<RequestSpec>,
    ) {
        val spec = RequestSpec().also(configure::execute)
        steps.add(WorkloadStep.http("POST", url, contentType, body, spec.headers, spec.captures))
    }

    /** Appends an external command. */
    public fun exec(vararg command: String) {
        steps.add(WorkloadStep.command(command.toList()))
    }
}

/**
 * The optional part of a request: headers it sends and values it takes out of its JSON answer.
 *
 * A captured value is available to every later step as `{{name}}` in the URL, headers, body and
 * command words — how a signed-in workload is written without curl and without a script:
 *
 * ```kotlin
 * workload {
 *     post("http://127.0.0.1:8080/auth/login", "application/json", """{"user":"demo"}""") {
 *         capture("token", "accessToken")
 *     }
 *     get("http://127.0.0.1:8080/api/home") { header("Authorization", "Bearer {{token}}") }
 * }
 * ```
 *
 * The path is dot-separated, an integer segment indexes an array: `user.id`, `items.0.sku`.
 */
public class RequestSpec {
    internal val headers: MutableMap<String, String> = LinkedHashMap()
    internal val captures: MutableMap<String, String> = LinkedHashMap()

    /** Sends this header; the value may use `{{name}}` captured earlier. */
    public fun header(
        name: String,
        value: String,
    ) {
        headers[name] = value
    }

    /** Reads [path] out of the JSON response into `{{variable}}`. */
    public fun capture(
        variable: String,
        path: String,
    ) {
        captures[variable] = path
    }
}

/**
 * The Jib mode: `jibAotTrain` and `jibAotVerify` run the image in a container, and an application
 * that needs its database or a broker to start needs that container on a network with them and
 * with their addresses in its environment. [dockerRunArgs] go on the `docker run` command line
 * after `--rm`, before the image: `--network`, `-e`, `--add-host`, whatever the stand needs.
 *
 * ```kotlin
 * zavarnik {
 *     jib { dockerRunArgs("--network", "stand_default", "-e", "DB_URL=jdbc:postgresql://postgres:5432/app") }
 * }
 * ```
 */

/**
 * A CRaC checkpoint of the warmed-up process, for the images that take one.
 *
 * Only one thing about an application cannot be worked out from the build: which of its outgoing
 * connections a checkpoint may leave open. A JVM refuses to checkpoint while any socket is open,
 * and the ones a server holds are its listening socket — which the plugin handles by itself — and
 * whatever it keeps to a database, a broker or a cache, which only the build knows about.
 *
 * Naming a port here does **not** close the connection; it leaves it in the snapshot, dead on
 * restore, for whoever owns it to notice. A pool that validates a connection before handing it out
 * (HikariCP does) and a client that reconnects both recover; one that does neither will use a dead
 * connection, which is why the restore is verified with a request that reaches the far side rather
 * than with a process that started. The alternative — closing them at checkpoint — was measured and
 * is worse: a pool opens replacements while the checkpoint is being taken and the checkpoint fails
 * on a socket that did not exist when it started (`docs/research/research-crac.md` §1.7).
 */
public abstract class CracSpec {
    /**
     * Remote ports whose sockets the checkpoint may leave open: `5432` for Postgres, `9092` for a
     * broker. Empty by default, which is right for an application that talks to nothing.
     */
    public abstract val ignoredRemotePorts: SetProperty<Int>

    /** `crac { ignoreRemotePort(5432, 9092) }`. */
    public fun ignoreRemotePort(vararg ports: Int) {
        ignoredRemotePorts.addAll(ports.toList())
    }

    /** The directory the snapshot is written to, under the output directory. `crac` by default. */
    public abstract val imageDirName: Property<String>
}

public abstract class JibSpec {
    /** Extra `docker run` arguments for the training and verification containers. Empty by default. */
    public abstract val dockerRunArgs: ListProperty<String>

    /** Adds to [dockerRunArgs]. */
    public fun dockerRunArgs(vararg args: String) {
        dockerRunArgs.addAll(args.toList())
    }
}

/** What `aotVerify` checks. */
public abstract class VerifySpec {
    /**
     * The share of the application's classes that must come from the cache, `0.0`–`1.0`. `0.9` by
     * default. A cache that is accepted but empty for the application means the training run did
     * not exercise it, and neither the JVM's exit code nor the jar manifest can tell.
     */
    public abstract val minCachedShare: Property<Double>

    /** Whether `check` depends on `aotVerify`. `true` by default. */
    public abstract val onCheck: Property<Boolean>

    /**
     * JVM arguments production adds on top of the start script — an agent, `--add-modules`, a
     * `JAVA_OPTS` of its own. `aotVerify` runs with them, so a mismatch with the training run is
     * found here and not in production, where the JVM rejects the cache with three lines on
     * stderr and exit code 0. Anything the application needs in *both* runs belongs in
     * [ZavarnikExtension.jvmArgs] instead: an agent present at training time and at run time is
     * fine; one present on one side only is not.
     */
    public abstract val jvmArgs: ListProperty<String>

    /** Adds to [jvmArgs]. */
    public fun jvmArgs(vararg args: String) {
        jvmArgs.addAll(args.toList())
    }
}

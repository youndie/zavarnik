package io.github.youndie.zavarnik

import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
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

/** The workload: external commands, run in order, each with its own exit code checked. */
public abstract class WorkloadSpec {
    /** The commands, each as its argument list. */
    public abstract val commands: ListProperty<List<String>>

    /** Appends a command. */
    public fun exec(vararg command: String) {
        commands.add(command.toList())
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
}

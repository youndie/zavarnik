# zavarnik

[![check](https://github.com/youndie/zavarnik/actions/workflows/check.yaml/badge.svg)](https://github.com/youndie/zavarnik/actions/workflows/check.yaml)

A Gradle plugin that gives a plain JVM application — Ktor, http4k, anything on the `application`
plugin — a [Project Leyden](https://openjdk.org/projects/leyden/) AOT cache: train it, **verify
that production will actually accept it**, and ship it inside the distribution. Spring Boot and
Quarkus have this built in; everything else has a two-command workflow that fails silently when
the environment differs. This plugin turns that silence into a red build.

*zavarnik* is a teapot: you brew the cache once and pour it into every start.

**Status:** works end to end, tested against real JDKs. Not yet on the Plugin Portal; every push
to `main` publishes a snapshot to the portfolio's repository, so the plugin id below resolves with
one extra block in `settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        maven("https://reposilite.kotlin.website/snapshots") {
            content { includeGroupByRegex("io\\.github\\.youndie.*") }
        }
        gradlePluginPortal()
    }
}
```

and `id("io.github.youndie.zavarnik") version "0.1.0.<run>"` — the latest is in the repository's
[`maven-metadata.xml`](https://reposilite.kotlin.website/snapshots/io/github/youndie/zavarnik/io.github.youndie.zavarnik.gradle.plugin/maven-metadata.xml).

## Requirements

- **JDK 25 or newer** as the project's toolchain — the one-step training workflow is JEP 514
  (JDK 25). Prefer 25.0.4+ / 26.0.2+: earlier builds never check the jars against the cache
  ([JDK-8377932](https://bugs.openjdk.org/browse/JDK-8377932)); the plugin checks them itself and
  warns.
- **The `application` plugin.** The cache is trained through the start script over the
  `lib/*.jar` layout; a classpath of directories, which is what `run` uses, yields no cache.
- **Linux or macOS for training** — the training run is stopped with `SIGTERM`, and a killed JVM
  writes no cache. Production on Windows works; training on it does not yet.
- **The same JDK build in production as in training**, down to the image: the JVM compares the
  build string and the size of `lib/modules`, and the `-jre` package of the same Temurin build
  has a different one. Train in the image that runs: every distribution carries
  `lib/zavarnik-runner.jar`, which trains and verifies on a bare JRE without Gradle, and the
  sample's Dockerfile shows how.
- **Ship `installDist` or `distTar`.** A zip cannot carry the cache: DOS timestamps are local time,
  and the JVM checks jar mtimes.
- **No wildcard on the start script's classpath.** The JVM records the classpath string and
  compares it at the next start, and it expands `lib/*` in whatever order the filesystem answers —
  Docker's overlay2 on a CI runner and containerd on a k0s node answered differently, and the
  first cache to reach a cluster was refused there, silently. `aotVerify` cannot see this: it runs
  where the training ran. The plugin refuses a wildcard at `installDist`; list the jars instead.
- Gradle 9 (developed and tested on 9.7.1).

## Quickstart

1. Apply the plugin next to `application` and say how to tell the app is up and what to hit:

   ```kotlin
   plugins {
       application
       id("io.github.youndie.zavarnik")
   }

   zavarnik {
       training {
           readyWhen.url("http://127.0.0.1:8080/health")
           workload {
               get("http://127.0.0.1:8080/api/warm")
               post("http://127.0.0.1:8080/api/order", "application/json", """{"items":[]}""")
           }
       }
   }
   ```

   The workload is what the cache holds, so it should be the hot path. A signed-in hot path
   needs no curl and no script: a step can send headers and take values out of its JSON answer
   for the steps after it, as `{{name}}` (dot path, an integer segment indexes an array):

   ```kotlin
   workload {
       post("http://127.0.0.1:8080/auth/login", "application/json", """{"user":"demo"}""") {
           capture("token", "accessToken")
       }
       get("http://127.0.0.1:8080/api/home") { header("Authorization", "Bearer {{token}}") }
   }
   ```

2. `./gradlew check` — `aotTrain` runs the installed application through its own start script
   with `-XX:AOTCacheOutput`, waits for the readiness URL, runs the workload, stops the JVM and
   waits for the cache to be assembled; `aotVerify` then fails the build unless production would
   accept the result (below).

3. `./gradlew distTar` or `docker build` — the start scripts pick the cache up when it is there,
   the tar carries it, and [`samples/ktor/Dockerfile`](samples/ktor/Dockerfile) trains it on the
   very image that runs it.

An application that cannot start on the build machine — no database, no broker — sets
`training { onAssemble = false }`: `assemble` and `build` then pack no cache instead of failing
in `aotTrain`, `distTar` still ships a cache a training run has left in `installDist`, and the
training happens where the application can run, through the runner below.

### Without Gradle: the runner

The cache has to be trained by the JVM that will use it, and the JVM that will use it usually
lives in a `-jre` image with no Gradle and no curl. So the distribution's `lib/` carries the
plugin's own logic as one self-contained jar, next to `zavarnik.properties` with the `zavarnik { }`
block as the runner reads it:

```dockerfile
FROM eclipse-temurin:25.0.4_7-jdk AS build
COPY . /src
RUN cd /src && ./gradlew installDist --no-daemon

FROM eclipse-temurin:25.0.4_7-jre
COPY --from=build /src/build/install/app /opt/app
RUN java -cp /opt/app/lib/zavarnik-runner.jar io.github.youndie.zavarnik.runner.Main train /opt/app \
 && java -cp /opt/app/lib/zavarnik-runner.jar io.github.youndie.zavarnik.runner.Main verify /opt/app
CMD ["/opt/app/bin/app"]
```

`train` is `aotTrain` and `verify` is `aotVerify` — the tasks are wrappers over the same classes,
read the same file and fail with the same messages; a failed `verify` leaves no image. On the
sample this takes the image from 647 MB (both stages `-jdk`) to 562 MB, with every application
class still coming from the cache.

### Jib and the Ktor plugin

`ktor { docker { } }` is Jib underneath, and Jib's default layout puts `/app/classes` and
`/app/resources` on the classpath — directories, for which the JVM writes no cache at all. With
Jib applied the plugin refuses that at configuration time and adds two tasks:

```kotlin
plugins {
    id("io.ktor.plugin") version "3.5.2"          // or com.google.cloud.tools.jib directly
    id("io.github.youndie.zavarnik")
}

jib { containerizingMode = "packaged" }          // jars only; the plugin refuses the default
```

```bash
./gradlew jibAotTrain     # jibDockerBuild, then the runner trains inside a container of that image
./gradlew jibAotVerify    # jibDockerBuild again — now with the cache as a layer — and verifies inside it
./gradlew jib             # the image with the cache, wherever jib pushes it
```

Jib reads its configuration once per build, so the first training and the first verification
are two invocations; from then on the cache is a layer of every Jib build and `-XX:AOTCache` is
in the entrypoint. The runner runs as the host user with `build/zavarnik/jib/` mounted, so the
cache lands on the host and Jib's file timestamps are never touched. An application that needs
its database to start gets the stand's network and environment through
`zavarnik { jib { dockerRunArgs("--network", "stand_default", "-e", "DB_URL=…") } }`. What this mode costs: a
Docker daemon on the build machine — the one thing Jib let a build do without. `jib` straight to
a registry, with no daemon, builds an image without a cache. Jib 3.5.4 itself does not support
Gradle's configuration cache; a build that has it on runs the Jib tasks with
`--no-configuration-cache`.

## The red build

`aotVerify` is the point. It checks three independent things and names the one that failed:
the jars in `lib/` match the manifest the training wrote, the application starts with the cache
made mandatory (`-XX:AOTMode=on`), and at least 90% of its classes come from the cache.

A jar rebuilt after training — caught by the manifest, before anything is started:

```
> zavarnik: the jars in lib/ are not the ones app.aot was trained against:
    - ktor-sample.jar: changed since aotTrain
  Run aotTrain again after every change to the classpath.
```

A jar merely touched — the JVM refuses it, and the build says why in the JVM's own words:

```
> zavarnik: the application did not start with the cache under -XX:AOTMode=on. The JVM's reasons:
    [0.009s][warning][aot] This file is not the one used while building the AOT cache:
                           '.../lib/ktor-sample.jar', timestamp has changed
    [0.009s][error  ][aot] shared class paths mismatch
```

Without the plugin both cases are three lines on stderr and exit code 0 — the application runs,
just without the cache, and nobody notices until someone measures.

## What the report shows

`./gradlew aotReport` on the Ktor sample, JDK 25.0.4, Linux, 10 runs per variant:

| Variant | Readiness, median | Requests in 20 s | C1 methods | C2 methods |
|---|---|---|---|---|
| without the cache | 649 ms | 83 296 | 3587 | 1171 |
| with `app.aot` | 233 ms | 99 570 | 3388 | 1172 |

Two tables on purpose. Readiness improves two- to threefold. The JIT work after the start does
not: the cache holds classes, heap objects and method profiles, not compiled code, so C2 compiles
the same thousand-odd methods either way. The extra fifth of requests in the cached window is
where the profiles (JEP 515) do help: the JIT starts on the hot methods at once instead of
discovering them. A report that showed only the first table would promise a warm service the
JVM does not deliver.

## Findings the plugin is built on

- The cache survives relocation to another absolute path, but not a touched jar, a prepended
  classpath entry, `--add-modules`, a `-javaagent` on one side only, or a change of the
  compressed-oops boundary (ZGC ↔ the other collectors). An agent or ZGC on *both* sides is fine.
- Found while building this: OpenJDK 25.0.0–25.0.3 and 26.0.0–26.0.1 never validate the
  application jars against a cache made by the one-step workflow — a stale cache is used
  silently, exit code 0. The bug is [JDK-8377932](https://bugs.openjdk.org/browse/JDK-8377932),
  fixed upstream in 25.0.4 and 26.0.2; which builds it affects, and that `-XX:AOTMode=on` does
  not catch it, is measured here.
- The cache is written on any exit except `SIGKILL`, `Runtime.halt` included, so training needs
  no hook inside the application.
- The JVM caches machine code for the training CPU's instruction set. The plugin turns that off
  by default (`portability = true`); it costs nothing measurable.

Every one of these has a script and a log behind it in [`experiments/`](experiments/); the
reasoning is in [`docs/research/`](docs/research/), written for a coding agent first: every claim
carries a path to where it was verified.

## Also in this repository

[`bench/`](bench/) is a Ktor CIO benchmark service with a profiling harness that attributes every
CPU and allocation sample to where the code came from. It was built to justify a second phase —
a Kotlin bytecode optimizer — and closed it instead: user code owns 1–4% of the CPU and 3–10% of
the allocations of a Ktor service, and R8 cannot even serve as the baseline on this stack. The
negative result, with numbers, is [`docs/research/research-optimizer.md`](docs/research/research-optimizer.md).

On a service not written for it — [konekt](https://github.com/youndie/konekt), Ktor CIO with
Exposed and Postgres under a one-core limit — the cache trained inside the image took `docker start`
to `/health` from 4.4 s to 2.0 s and the first request from 510 ms to 240 ms at the median of ten
restarts each; the record is in that repository's `docs/research/measurements-2026-09-07/aot/`.
In its cluster, with the cache trained in the release pipeline and the readiness probe retuned to
ask every second, the pod is Ready 3 s after its container starts, against 11 s before.

Two write-ups: [OpenJDK 25.0.0–25.0.3 uses a stale AOT cache without saying so](https://kotlin.website/blog/stale-aot-cache-on-jdk25)
and [User code is 1–4 % of a Ktor service's CPU](https://kotlin.website/blog/user-code-share-of-a-ktor-service).

| Directory | What it is |
|---|---|
| `zavarnik-gradle-plugin/` | the plugin: tasks, start-script guard, checks, TestKit tests |
| `zavarnik-runner/` | what the tasks call and what `lib/zavarnik-runner.jar` runs: training, verification, no Gradle |
| `samples/ktor/` | a Ktor server on the plugin, with the Dockerfile and an in-container check |
| `samples/ktor-jib/` | the same server on `ktor { docker { } }`, trained and verified through Jib |
| `bench/` | the benchmark service and the profiling harness |
| `experiments/` | the experiments the research cites, scripts and logs |
| `docs/` | layered documentation; start at [`docs/README.md`](docs/README.md); the plan is [`backlog.md`](backlog.md) |

## Checks

```bash
pip install pyyaml && make check          # the documentation gate
./gradlew check                           # the plugin: unit, TestKit and lint
./gradlew -p samples/ktor check distTar   # the sample, end to end
```

## License

MIT — see [LICENSE](LICENSE).

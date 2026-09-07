# zavarnik

A Gradle plugin that gives a plain JVM application — Ktor, http4k, anything on the `application`
plugin — a [Project Leyden](https://openjdk.org/projects/leyden/) AOT cache: train it, **verify
that production will actually accept it**, and ship it inside the distribution. Spring Boot and
Quarkus have this built in; everything else has a two-command workflow that fails silently when
the environment differs. This plugin turns that silence into a red build.

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

`aotTrain` runs the installed application through its own start script with `-XX:AOTCacheOutput`,
waits for the readiness URL, runs the workload, stops the JVM with `SIGTERM` and waits for the
cache to be assembled. `aotVerify` — on `check` by default — fails the build unless three
independent things hold: the jars in `lib/` match the manifest the training wrote, the
application starts with the cache made mandatory (`-XX:AOTMode=on`), and at least 90% of its
classes come from the cache. The start scripts pick the cache up when it is there, `distTar`
ships it, and [`samples/ktor/Dockerfile`](samples/ktor/Dockerfile) trains it on the very image
that runs it. `aotReport` measures what the user gets — and what they do not.

**Status:** the plugin works end to end and is covered by unit and TestKit tests against real
JDKs; the release (a GitHub remote, the Plugin Portal) is ahead. What the JVM validates before it
uses a cache, what a training run has to do for the cache to be written at all, and what the cache
does to the JIT — all of it is measured, not assumed, and recorded in
[`docs/research/`](docs/research/), with the experiments and their logs committed under
[`experiments/`](experiments/).

## What the report shows

`./gradlew aotReport` on the Ktor sample, JDK 25.0.4, Linux, 10 runs per variant:

| Variant | Readiness, median | Requests in 20 s | C1 methods | C2 methods |
|---|---|---|---|---|
| without the cache | 649 ms | 83 296 | 3587 | 1171 |
| with `app.aot` | 233 ms | 99 570 | 3388 | 1172 |

Two tables on purpose. Readiness improves two- to threefold. The JIT work after the start does
not: the cache holds classes, heap objects and method profiles (JEP 515), not compiled code, so C2
compiles the same thousand-odd methods either way. A report that showed only the first table
would promise a warm service the JVM does not deliver.

## Findings the plugin is built on

- The cache survives relocation to another absolute path, but not a touched jar, a prepended
  classpath entry, `--add-modules`, a `-javaagent` on one side only, or a change of the
  compressed-oops boundary (ZGC ↔ the other collectors). An agent or ZGC on *both* sides is fine.
- OpenJDK 25.0.0–25.0.3 and 26.0.0–26.0.1 never validate the application jars against a cache
  made by the one-step workflow ([JDK-8377932](https://bugs.openjdk.org/browse/JDK-8377932)):
  a stale cache is used silently, exit code 0. The plugin verifies the jars itself.
- The cache is written on any exit except `SIGKILL`, `Runtime.halt` included, so training needs
  no hook inside the application.
- "The same JDK" means the same image: the `-jdk` and `-jre` packages of one Temurin build have
  different `lib/modules`, and the JVM rejects the cache by its size.
- A zip cannot carry the cache — DOS timestamps are local time — and a tar can, if the jars are
  pinned to the timestamp Gradle stamps on a reproducible tar. `aotTrain` does that.
- The JVM caches machine code for the training CPU's instruction set. The plugin turns that off
  by default (`portability = true`); it costs nothing measurable, and a cache trained on an
  EPYC with AVX-512 runs on a Core Ultra without it either way.

Every one of these has a script and a log behind it in [`experiments/`](experiments/).

## The benchmark, and the second phase that did not survive it

[`bench/`](bench/) is a Ktor CIO service with three kinds of endpoint — echo, JSON CRUD over an
in-memory store, and a "business" endpoint written the way services are written — plus a
harness that measures throughput with `oha` while `async-profiler` samples CPU and allocations,
and an attribution script that splits every sample by where the code came from: user code,
Ktor, kotlinx, the stdlib, the JDK. It also runs against a real service from inside its
container ([`bench/profile/konekt.sh`](bench/profile/konekt.sh)).

It was built for a second phase — a Kotlin/JVM bytecode optimizer, a K2 IR compiler plugin plus an
ASM transform, judged against R8 as the existing tool
([`docs/research/research-optimizer.md`](docs/research/research-optimizer.md)). The gate question
was how much of a Ktor service's profile user code even owns. The answer closed the phase:

- on the benchmark's business endpoint user code owns 2% of the CPU and 10% of the allocations;
  on a real service, 1–4% and 3–5% — the rest is Ktor, coroutines, the stdlib and the database
  driver, none of which an IR plugin over user code can touch;
- R8 could not serve as the baseline at all: on this stack it rewrites `invokespecial` on
  Kotlin interface default methods to the declaring interface, which the JDK 25 verifier rejects
  — a bug with a `javap` reproduction, not a configuration problem;
- of the candidate rewrites, boxing and lazy logging fell under the brief's own thresholds; the
  regex compiled per request (6% of bytes) is a one-line manual fix.

What survived: the benchmark, the attribution methodology, and two findings for upstream —
the R8 rewrite and a `KClassImpl.toString` on every `receive<T>()` in Ktor. The method-size
diagnostic moved to the portfolio's lint ([youndie/sborka#28](https://github.com/youndie/sborka/issues/28)).

## Layout

| Directory | What it is |
|---|---|
| `zavarnik-gradle-plugin/` | the plugin: tasks, start-script guard, checks, TestKit tests |
| `samples/ktor/` | a Ktor server on the plugin, with the Dockerfile and an in-container check |
| `bench/` | the benchmark service and the profiling harness |
| `experiments/` | the experiments the research cites, scripts and logs |
| `docs/` | layered documentation for a coding agent; start at [`docs/README.md`](docs/README.md) |
| `backlog.md` | the plan, one file per item under `docs/backlog/` |

## Checks

```bash
pip install pyyaml && make check          # the documentation gate
./gradlew check                           # the plugin: unit, TestKit and lint
./gradlew -p samples/ktor check distTar   # the sample, end to end
```

## License

MIT — see [LICENSE](LICENSE).

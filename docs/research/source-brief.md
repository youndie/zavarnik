---
id: source-brief
title: Исходный бриф — Gradle-плагин для AOT-кэша Leyden
type: research
status: active
date: 2026-09-06
---

> Бриф, с которого начался проект, — как он был написан 06.09.2026, до ресёрча. Здесь он лежит
> **без правок**: [research-architecture](research-architecture.md) отмечает, какие его посылки
> подтвердились, какие перевернулись и какие остались гипотезами. Анкер кода: ни одного, документ
> предшествует коду — см. `experiments/aot-validation/run.sh` для проверок, которые из него выросли.

# Research Brief: Gradle plugin for Project Leyden AOT caches in non-framework JVM apps

**Status:** draft, pre-research  
**Date:** 2026-09-06  
**Owner:** Pavel  
**Time-box:** 4 weeks (1 week research, 3 weeks build + validation)

---

## 1. Problem statement

Project Leyden (JDK 24–26) makes JVM startup and warmup dramatically faster via an AOT cache, with no application code changes. The workflow is two commands, but in practice it is fragile: the cache is silently rejected on environment mismatch, custom classloaders defeat it, and Docker packaging can invalidate it. Spring Boot and Quarkus have absorbed this complexity into their own build tooling. Everything else — Ktor, http4k, plain `application`-plugin apps, CLIs — has nothing.

**Hypothesis:** a small Gradle plugin that automates *train → verify → package* for plain JVM/Kotlin apps is (a) technically feasible with today's JDK, (b) not going to be obsoleted by Gradle or Ktor within the horizon that matters, and (c) will get at least one external reaction within 4 weeks of release.

## 2. Goals / non-goals

**Goals**
- Confirm the AOT cache survives a realistic deploy path (build on CI → dist archive → container → different filesystem location).
- Identify every condition that invalidates the cache and decide which the plugin must *detect* vs. *document*.
- Ship an MVP that makes a Ktor app start measurably faster with one plugin ID and a training config block.

**Non-goals**
- Peak-throughput improvements (AOT code compilation is not in mainline yet).
- Spring Boot / Quarkus support (already solved upstream).
- Native Image / CRaC integration.

## 3. Background (verified facts, as of 2026-09)

| JDK | JEP | What it adds | Consequence for the plugin |
|---|---|---|---|
| 24 | 483 | AOT class loading & linking; `-XX:AOTMode=record` + `-XX:AOTMode=create` | Two-step pipeline |
| 25 | 514 | `-XX:AOTCacheOutput=app.aot` records and assembles on shutdown | Single-step pipeline; **minimum supported JDK for MVP** |
| 25 | 515 | AOT method profiles — JIT starts warm | Training workload quality matters, not just class loading |
| 26 | 516 | AOT object caching with any GC, incl. ZGC | Pre-26: warn or reject ZGC |
| — | 8335368 (draft) | AOT code compilation (premain prototype only) | Out of scope; design the cache path so it can be added later |

Known constraints (from JEP 483 and Quarkus's integration notes):
- Training and production runs must use the same JDK build, OS, CPU architecture, and should pin the same GC.
- Custom classloaders are not supported — the app must load through JDK loaders (rules out fat jars with launcher loaders; the `application` plugin's `lib/*.jar` layout is fine).
- The cache is written at JVM shutdown → training requires a graceful exit.

## 4. Research questions

Each RQ has a method and a pre-declared red/green outcome. Record results (and retractions) in `RESEARCH.md` in the repo.

### RQ1 — Path relocation
*Does a cache trained in `build/install/app/` load when the identical dist is unpacked at `/opt/app/`?*
- **Method:** train in `installDist` layout, copy dist to a different absolute path, run with `-XX:AOTCache -Xlog:aot=info -Xlog:cds=info`. Repeat with relative-path classpath in the start script vs. absolute.
- **Green:** loads at any location, or loads when the classpath is expressed relative to `$APP_HOME`.
- **Red:** paths are validated as strings → training must happen in the final layout (Docker stage). This changes the plugin's shape but does not kill it.

### RQ2 — Jar identity checks
*Which jar attributes are validated: size, mtime, hash, path?*
- **Method:** touch jars, re-copy jars, `COPY` into a Docker image, build with Jib (epoch mtimes).
- **Green:** size+path only, or mtime survives Jib normalization.
- **Red:** mtime is checked and Docker `COPY` breaks it → plugin must own the packaging step or enforce Jib/`--mtime` normalization.

### RQ3 — Classpath superset rules
*Can the runtime classpath append entries not present at training (agents, config dirs)?*
- **Method:** append `-cp` entries, `-javaagent`, `--add-modules` at runtime.
- **Green:** prefix-match semantics hold as documented for CDS.
- **Red:** any deviation rejects the cache → `aotVerify` must diff the exact command line.

### RQ4 — Kotlin-specific archiving
*Are Kotlin's generated classes (indy lambdas, `kotlinx.coroutines` state machines, `kotlinx.serialization` serializers) actually archived, or do they fall back to runtime generation?*
- **Method:** count archived classes via `-Xlog:aot` on a Ktor + coroutines + serialization sample; compare `-Xlambdas=indy` vs. class-based lambdas.
- **Green:** ≥90% of loaded app classes come from the cache.
- **Red:** significant fallback → document compiler flags that maximize hit rate; this is a differentiator, not a blocker.

### RQ5 — Training workload ergonomics
*What is the minimal reliable way to run a workload and stop the JVM gracefully from Gradle?*
- **Method:** prototype three modes: (a) readiness URL + external command + SIGTERM, (b) `exitAfter` timeout, (c) app-internal hook (`Runtime.halt` is not acceptable — cache won't be written).
- **Red:** SIGTERM on Windows cannot trigger shutdown hooks reliably → Windows training unsupported in MVP (document).

### RQ6 — Measured benefit on a real target
*What is the startup/readiness delta on a Ktor app?*
- **Method:** time-to-first-200 on `/health`, 20 runs each, with and without cache, SerialGC and G1, JDK 25 and 26.
- **Green:** ≥40% reduction in median readiness (consistent with published Spring/Quarkus numbers).
- **Red:** <20% → the value proposition is too weak to publish; stop.

### RQ7 — Obsolescence risk
*Is Gradle, Ktor, or JetBrains building this?*
- **Method:** search Gradle issue tracker, Ktor YouTrack, Kotlin Slack, JVM Weekly; check Gradle 9.x roadmap.
- **Red:** an upstream implementation is announced or in progress → publish findings, do not build.

## 5. Prior art

- **Quarkus** — `aot-jar` packaging + training via integration tests. Framework-specific.
- **Spring Boot** — CDS/AOT support in the launcher, extracted layout. Framework-specific.
- **Leyden premain README** — reference for flags and phases.
- **JVM Weekly #169 "Diagnosing Your Leyden AOT Cache"** — starting point for the verification log parsing in `aotVerify`.
- No generic Gradle plugin found on the Plugin Portal as of this date (re-verify under RQ7).

## 6. Proposed MVP (contingent on RQ1–RQ3 green or yellow)

```kotlin
plugins {
    application
    id("website.kotlin.leyden")
}

leyden {
    jvmArgs("-XX:+UseSerialGC")            // pinned for both train and run
    training {
        readyWhen.url("http://localhost:8080/health")
        workload { exec("curl", "-s", "http://localhost:8080/api/warm") }
        // alternative: exitAfter = 15.seconds
    }
}
```

**Tasks**
- `aotTrain` — runs the `installDist` layout with `-XX:AOTCacheOutput`, waits for readiness, executes workload, sends graceful shutdown.
- `aotVerify` — starts with `-XX:AOTCache -Xlog:aot=info`, parses the log, **fails the build** if the cache was rejected, reporting the reason. This is the core value.
- `aotReport` — cold vs. cached readiness timing table (also the Show HN artifact).
- Wiring: `startScripts` gets `-XX:AOTCache=$APP_HOME/lib/app.aot`; `distZip`/`installDist` include the cache. Jib integration second.
- Toolchain checks: JDK ≥ 25 required; ZGC on < 26 → error; mismatched `jvmArgs` between train and run → error.

Estimated size: 300–500 lines of Kotlin on the Gradle API plus TestKit coverage for every invalidation condition found in RQ1–RQ3.

## 7. Kill criteria (any one is sufficient)

1. RQ1 and RQ2 both red *and* no packaging step the plugin can own (cache cannot be made to survive a normal deploy).
2. RQ6 red (<20% benefit on Ktor).
3. RQ7 red (upstream implementation in progress).
4. Four weeks after v0.1 release: zero external reactions (star, issue, mention, question).

## 8. Open questions

- Name and Maven coordinates (fits the existing `kotlin.website` namespace?).
- Should the plugin also support Maven, or is Gradle-only a deliberate scope cut for MVP? (Leaning: Gradle-only.)
- Whether to expose a "training via test task" mode: the test JVM is not the app JVM, so it only works if tests spawn the app as a process (Quarkus model). Defer.

## 9. References

- JEP 483 — https://openjdk.org/jeps/483
- JEP 514 — https://openjdk.org/jeps/514
- JEP 515 — https://openjdk.org/jeps/515
- JEP 516 — https://openjdk.org/jeps/516
- Leyden premain README — https://github.com/openjdk/leyden/blob/premain/README.md
- Leyden design note on training runs — https://openjdk.org/projects/leyden/notes/05-training-runs
- Quarkus AOT guide — https://quarkus.io/guides/aot
- Quarkus Leyden integration write-up — https://quarkus.io/blog/leyden-2/
- JVM Weekly #169, cache diagnostics — https://www.jvm-weekly.com/p/diagnosing-your-leyden-aot-cache

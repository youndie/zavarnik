---
id: research-jit-constructs
title: "zavarnik, fifth phase — what C2 does to a Ktor + Exposed request path"
type: research
status: active
date: 2026-09-19
---

# Research: which Kotlin constructs C2 handles badly, and whether this stand can tell

The fifth phase — [brief](source-brief-jit-constructs.md): rank the Kotlin/JVM constructs on the
request path of a Ktor + Exposed service by how well HotSpot C2 optimises them, and price each
ranking as a share of request CPU. The brief gates the detailed work behind a macro check (RQ0)
and stops the study if the share is too small to matter.

This document is written in **English**, unlike the four phases before it — see
[docs/README.md](../README.md) for what that costs and why.

It records **verified facts** (read in artefacts and JDK configuration files, or measured by the
runs named beside them), **decisions**, and **risks**. Anything not verified is called a
hypothesis and says where it gets settled.

The phase does not start from zero, and that is the first thing to say about it. The second phase
([research-optimizer](research-optimizer.md)) profiled this exact stack to answer a different
question, and the fourth ([research-engines](research-engines.md)) rebuilt the stand and found
that the third phase's protocol had never pinned anything. Between them they already measured most
of the denominators this brief's thresholds are divided by. §1.1 collects them, because a study
that re-derives them will spend its budget arriving where the repository already is.

Three of the brief's premises did not survive contact with the JDK it pins. The toggle RQ1 asks for
does not exist on a product VM (§1.2). The instrument its steady-state gate is defined against
reports no compilation at all on the settings it ships with, so that gate cannot fail (§1.3). And
the relevance threshold the whole method rests on — 2 % of request CPU — is below the resolution of
the macro instrument on this host (§1.7). Each is marked *deviation from the brief* where it
appears.

Four of its questions also changed shape before any stand ran, which is the more useful result.
RQ1's suspicion is right and aimed at the wrong code: the oversized suspend bodies on every request
belong to Ktor and the Netty engine, not to the application, and nothing on the path is within a
factor of four of the huge-method limit (§1.8). RQ2 does not need to ask whether a megamorphic site
exists — one does by construction, 580 receivers on one call site with a type profile two wide
(§1.6). RQ5, scoped as the brief scopes it, is green by arithmetic before it is measured, because
application code is 0.9–4.0 % of a real service's CPU (§1.1) — D5 rescopes it to every owner, and
§1.18 then measures it there. And RQ6 looked like two answers separated by a single line of
application code (§1.5); it has one, and §1.16 says which. None of this required a measurement the brief planned;
all of it changes what the measurements should be.

Runs of 2026-09-19 are on the mac (Darwin aarch64, OpenJDK 25.0.2+10-69) and are committed under
`experiments/jfr-compiler-events/` — `run.sh` on a short workload, `long-run.sh` on one that keeps
the compiler busy, with the raw output of the second instrument beside every count, because a count
without its derivation cannot be argued with. The second script exists because the first one's
answer was wrong, which §1.3 records rather than quietly replaces. The figures quoted from
earlier phases were taken on the Linux box (Ubuntu 24.04 in WSL2, Core Ultra 7 255HX, 20 threads,
OpenJDK 25.0.4+7-1-24.04-Ubuntu). Which machine a number came from is stated with the number,
because §1.7 is about the machines.

---

## 1. Verified facts

### 1.1 The denominators this brief divides by are already measured

Nothing in this section is new work. It is here because the brief sets two thresholds — "2 % of
request CPU" for macro relevance and "10 % of p50 latency" for the RQ0 gate — and both are shares
of quantities this repository has already profiled twice.

| Fact | Where verified |
|---|---|
| On the stand's most business-like endpoint, application code owns **2.1 % of CPU and 9.9 % of allocated bytes** by owner; the rest by owner is kotlinx 73.9 %, Ktor 15.5 %, stdlib 8.4 % of CPU | [research-optimizer](research-optimizer.md) §1.4, `bench/profile/results/baseline/business.cpu.collapsed` |
| On a real service — konekt, Ktor CIO with Exposed and Postgres — application code owns **4.0 % of CPU and 3.4 % of bytes** at 50 rps, and 0.9 % / 5.4 % at 200 rps. By owner at 50 rps: kotlinx 21.7 %, Postgres driver 18.2 %, Ktor 13.0 %, stdlib 8.8 %, Exposed 4.7 % of CPU; stdlib 26.6 %, Ktor 24.8 %, kotlinx 13.6 %, Exposed 13.5 % of bytes | [research-optimizer](research-optimizer.md) §1.8, `bench/profile/results/konekt-screens-50/` |
| Swapping the engine moves the application's share more than anything the application does: handlers own **6.1 % of CPU on Netty against 3.1 % on CIO** — the same handlers, on an engine that does less | [research-engines](research-engines.md) §1.4, `bench/profile/results/engine-{cio,netty}/` |
| Netty costs **82 µs of CPU per request** on that endpoint against CIO's 166; on Netty the engine category itself is 43.1 % of CPU and the "hand-off machinery" 1–2.5 %, against CIO's 33–48 % | [research-engines](research-engines.md) §1.3, §1.7 |
| Statically, **10 of 283** application methods exceed `FreqInlineSize`; none exceeds 8000 bytes. Dynamically, of 858 "too big" inlining refusals under load, five name application methods and all five read "hot method too big": `Pricing::quote` 1827 b twice, an `invokeSuspend` 816 b twice, a generated `deserialize` 374 b once | [research-optimizer](research-optimizer.md) §1.4, `bench-results/inlining2/service.log` |
| On a container-limited real service at 50 rps, **61 % of self CPU is the JVM's own threads**, and the frames are C2's (`PhaseChaitin::Split`, `IndexSetIterator`) | [research-optimizer](research-optimizer.md) §1.8 |
| Run-to-run spread on this stand: **±13–15 % on rps**, 2–9 % on µs of CPU per request, for the same variant | [research-engines](research-engines.md) §1.5 |

**Consequence 1 — RQ0 asks about a bucket whose inside is already known, and the gate will pass.**
The brief's RQ0 counts application, Ktor, Exposed, serialisation and the JDBC driver as one
quantity and asks whether it clears 10 % of p50 latency. On konekt that bucket is essentially the
whole process outside the driver's socket wait, so the gate passes and tells nobody anything. The
question with an answer in it is the one the second phase asked instead: *which owner inside the
bucket*. That answer exists, and it says the mass is kotlinx, then the driver, then Ktor — with
application code a rounding error.

**Consequence 2 — the 2 % macro threshold is unreachable for any construct confined to application
code.** All of application code is 0.9–4.0 % of CPU on a real service. A construct that occurs
only there cannot reach 2 % of request CPU unless it is *half of everything the application does*.
RQ5 as written — value classes, `$default` methods, delegated properties, capturing lambdas — is
therefore a green verdict by arithmetic before a single measurement, and a green verdict reached
that way is the brief's own "grey" in disguise. This is a *deviation from the brief*: the
constructs have to be counted wherever they occur, and kotlinx.serialization, Ktor and Exposed are
Kotlin too. See D5.

**Consequence 3 — pinning Netty raises the ceiling, and not for the reason the brief gives.** The
brief chose Netty as "the most common production configuration". Measured, it is also the choice
that doubles the application's share of CPU and removes a third of the profile that belongs to the
dispatcher queue. That is the difference between a study whose subject is 3 % of the profile and
one whose subject is 6 %, and it should be stated as the reason rather than discovered later.

### 1.2 The C2 thresholds, and which of the brief's toggles a product JVM actually has

| Fact | Where verified |
|---|---|
| `FreqInlineSize=325` (a `C2 pd product` flag), `MaxInlineSize=35`, `InlineSmallCode=2500`, `MaxInlineLevel=15`, `C1MaxInlineSize=35`, `C1MaxInlineLevel=9`, `DontCompileHugeMethods=true`, `DoEscapeAnalysis=true`, `EliminateAllocations=true`, `TypeProfileWidth=2`, `PerMethodRecompilationCutoff=400`, `OmitStackTraceInFastThrow=true` | `java -XX:+PrintFlagsFinal -version`, OpenJDK 25.0.2+10-69, macOS/aarch64, 2026-09-19 |
| The same values on Linux/x86-64 and JDK 25.0.4, and `FreqInlineSize` is platform-dependent *by declaration* and equal on both platforms only *in fact* | sborka, `docs/research/research-perf-lint.md` §1.2 |
| **`HugeMethodLimit` is a `develop` flag and does not exist on a product VM**: `-XX:HugeMethodLimit=9000` is refused with "VM option 'HugeMethodLimit' is develop and is available only in debug version of VM", and `-XX:+UnlockDiagnosticVMOptions` does not change that | both commands, mac, 2026-09-19 |

**Consequence — RQ1 has one toggle, not two, and they measure different things** *(deviation from
the brief)*. The brief writes the 8000-byte limit as `DontCompileHugeMethods`, which conflates the
switch with the constant. `-XX:FreqInlineSize=<n>` is a real dial: it can be raised to find where
a refusal stops costing. The 8000 has no dial on a release build — the only available toggle is
`-XX:-DontCompileHugeMethods`, which removes the refusal entirely. That answers "what does not
compiling this method cost", which is a bound, not a knee, and it is worth having for exactly the
methods the second phase found (`Pricing::quote` at 1827 b is nowhere near 8000, so on this stand
the second toggle has no subject at all).

**Consequence — RQ7's exception arm has to name which exception.** `OmitStackTraceInFastThrow` is
on, but it applies to exceptions the JVM throws implicitly, not to ones library code constructs.
For the two the brief names: `kotlinx.coroutines.JobCancellationException` **overrides
`fillInStackTrace`** and is already stackless; `TimeoutCancellationException` **does not**, so
every `withTimeout` expiry walks the stack. "Exceptions used as control flow" is two different
prices, and the brief's toggle — "stackless exceptions" — is already applied upstream to one of
them.

| Fact | Where verified |
|---|---|
| `JobCancellationException` declares `public Throwable fillInStackTrace()` | `javap -p` on `org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.11.0!/kotlinx/coroutines/JobCancellationException.class` |
| `TimeoutCancellationException` declares no such override — its only additions are `coroutine` and `createCopy` | `javap -p` on `org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.11.0!/kotlinx/coroutines/TimeoutCancellationException.class` |

### 1.3 JFR reports no compilation on the settings it ships with, and a census on any other

The brief's Tooling table puts JFR down for "compiler behaviour on the live service **without
diagnostic flags**", and its measurement protocol starts only "after JFR shows no C2 compilations
for 60 seconds". Both are claims about an instrument, so they were tested against a second one: the
same runs also print every compilation under `-XX:+PrintCompilation`, and that count is what JFR is
held against. Two workloads — a single hot loop, which stops compiling almost immediately, and 3000
methods made hot one after another, which keeps the compiler busy for the whole run.

| Fact | Where verified |
|---|---|
| `profile.jfc` ships `jdk.Compilation` with **`threshold` 100 ms** and `jdk.CompilerInlining` with **`enabled` false**; `default.jfc` disables the inlining event too | `openjdk-25.0.2!/lib/jfr/profile.jfc` and `openjdk-25.0.2!/lib/jfr/default.jfc`, quoted at the head of each result log |
| **On the settings it ships with, JFR reports no compilation at all.** `settings=profile`, short workload: 1258 and 1275 compile tasks by `-XX:+PrintCompilation` against **0** `jdk.Compilation`. Long workload, two sweeps: **7268 and 7300** tasks over about 4.5 seconds, still **0** — and 0 `jdk.CompilerInlining`, 0 `jdk.CompilationFailure` | `experiments/jfr-compiler-events/results/`, the `shipped` arm of both scripts |
| `jdk.Deoptimization` is the one compiler event that does report on stock settings: 1–5 events per run, in every arm | the same logs |
| **Once `jdk.Compilation` is enabled explicitly, it is a census.** Long workload, `settings=none` with the event on and its threshold at 0: **6022 events against 6052 compile tasks** in the same id range in one sweep and **6015 against 6024** in the other — 99.5 % and 99.9 % | `results/*.long.log`, arm `bare` |
| What it misses is startup, not compilation: **886 and 933 tasks compiled before the recording was live**, all of them below the first id JFR reports | the same logs |
| **`jdk.CompilerInlining` is the event that truncates.** Eight recordings: it covers the **first 8 to 96 compile ids** of the recording and then stops, while `jdk.Compilation` in the very same recording runs on past 7000. Its event *count* is not the quantity to quote — 19, 33, 42, 80, 82, 87, 90, 342 — because one compilation makes many inlining decisions; the id span is what repeats | `results/*.long.log`, the `bare` arm plus three repeats, in each of two sweeps |
| The content is right where it arrives: `jdk.CompilerInlining` carries `succeeded` and the same refusal vocabulary as `-XX:+PrintInlining` — "callee is too large", "callee uses too much stack", "too big", "no static binding" — alongside the successes | `jfr print --events jdk.CompilerInlining` |
| **Watching costs something, and JFR costs more than the flag the brief calls diagnostic.** Medians of three interleaved repeats, two sweeps: no instrument 4.18 and 4.23 s; `jdk.Compilation` via JFR 4.40 and 4.47 s (**+5.3 %**, **+5.7 %**); `-XX:+PrintCompilation` 4.29 and 4.31 s (**+2.6 %**, **+1.9 %**). Within-variant spread is at most 1.4 %, so the ordering is not noise | `results/*.long.log`, section `cost` |
| **Between sweeps the absolute seconds moved and the ordering did not.** An earlier sweep of the same script on this machine ran the workload in about 6.7 s rather than 4.2. Only the ratios are compared across sweeps, and this row exists so that a reader adding a later number to this table knows that | the two committed `*.long.log` files |

**Correction found while controlling this section.** It first read that JFR's compiler view "is not
a census", on the strength of the short workload: some 60 events of 1340 compile tasks, inside a
window of about twenty milliseconds. That was the subject, not the instrument. A one-method loop
finishes compiling while the JVM is still starting, so nearly everything it compiles happens before
the recording's settings take effect, and what was left looked like truncation. The long workload
removes that explanation and the number goes to 99.5 %. The lesson is the one the phase is built
on: a negative result about an instrument needs a positive control, and a control that is too small
to contain the effect controls nothing.

**Consequence — the gate can be built, and the brief's reason for it is still wrong.** "No
compilations for 60 seconds" is a usable warm-up criterion on `jdk.Compilation`, provided the event
is switched on and its threshold lowered. On the settings the brief assumes, it is satisfied while
the JVM compiles at full tilt — the failure mode where a check that cannot find its subject scores
as a pass. And "without diagnostic flags" does not survive either: the event needs an explicit
setting on the command line exactly as `-XX:+PrintCompilation` does, and costs three times more on
a compile-heavy workload. D3.

**Consequence — inlining evidence cannot come from JFR, and the positive control sizes each event
separately.** `jdk.Compilation` switched on is a census and can carry a gate. `jdk.CompilerInlining`
switched on covers the first few dozen compile ids of the recording and nothing after, in eight
recordings out of eight. The two sit in the same recording and only one of them can be believed, so
every inlining refusal this study quotes has to come from `-XX:+PrintInlining` or `LogCompilation` —
the invasive instrument the brief's own Threats section says changes timing. The inlining runs and
the timing runs are therefore different runs, which is a cost of the method rather than an oversight
in it.

**Precedent, one phase earlier.** The second phase's first inlining measurement returned zero
mentions of application code in 4744 inlining decisions, and the zero was the instrument: without
`-XX:+PrintCompilation` the log does not name compilation roots ([research-optimizer](research-optimizer.md)
§1.4). The same error, the same repository, a different instrument — and this section made a
version of it again before the control caught it.

### 1.4 RQ4's subject is not the array

The brief suspects `ResultRow` "as `Array<Any?>`". Read in the artefact, the array is the cheap
half of what stands between a caller and a column value.

| Fact | Where verified |
|---|---|
| `ResultRow` holds `Object[] data`, a `Map<Expression<?>, Integer> fieldIndex`, and a `ResultRow$ResultRowCache lookUpCache`; a read goes `get → getInternal → getRaw → getExpressionIndex → data[i] → rawToColumnValue` | `javap -p` on `org.jetbrains.exposed:exposed-core:1.4.0!/org/jetbrains/exposed/v1/core/ResultRow.class` |
| `IColumnType<T>` has exactly **one abstract method**, `valueFromDB(Object)`, and twelve default methods, two of which are on the read path: `readObject(RowApi, int)` and `setParameter(...)` | `javap` on `org.jetbrains.exposed:exposed-core:1.4.0!/org/jetbrains/exposed/v1/core/IColumnType.class` |
| The package is `org.jetbrains.exposed.v1.core`; JDBC lives in a separate artifact, and the DAO layer the brief excludes is a third | the same jar; `exposed-jdbc`, `exposed-dao` in the Gradle cache |
| The versions present in this portfolio are **1.4.0** and 1.3.1 | `~/.gradle/caches/modules-2/files-2.1/org.jetbrains.exposed/` |

**Consequence — the brief's RQ4 toggle does not isolate what the brief says it isolates.**
Replacing `Array<Any?>` with a typed row holder removes the array *and* a hash lookup keyed by an
`Expression<?>` per column per row *and* the cache object. A two-arm comparison against
hand-written JDBC therefore prices all three at once and attributes the total to the array. Three
arms are needed: Exposed as shipped, an array without the map, and the typed holder. The
megamorphism the brief suspects is real in shape — after erasure the site calls
`Object valueFromDB(Object)` on an interface with one abstract method and many implementations —
but how many receivers a request actually sees is a runtime question, not a static one.

**Consequence — the setup table pins no version for the data layer**, while the brief's own rule
is that every version is pinned before the first measurement. "Ktor 3.x" is not a pin either. D7.

### 1.5 A JSON-only service loads one encoder, and one call anywhere loads three

RQ6's green condition is that the `Encoder` calls in a generated serialiser inline. The jar ships
six encoder implementations, so whether the call sites are monomorphic is a question about which of
them the process *loads* — a runtime fact, not a count of classes in an artifact. Measured with a
program shaped like the brief's list endpoint, compiled by the Kotlin version the stand pins and run
under `-verbose:class`.

| Fact | Where verified |
|---|---|
| `kotlinx-serialization-json-jvm` 1.11.0 ships six encoder implementations — `StreamingJsonEncoder`, `AbstractJsonTreeEncoder`, `JsonTreeEncoder`, `JsonTreeListEncoder`, `JsonTreeMapEncoder`, `JsonPrimitiveEncoder` — and the core artifact adds `AbstractEncoder`, `TaggedEncoder`, `NamedValueEncoder`, `NoOpEncoder` | `unzip -l` on `org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.11.0!/kotlinx/serialization/json/internal/` |
| **Encoding and decoding through strings loads exactly one concrete encoder and one concrete decoder** — `StreamingJsonEncoder` and `StreamingJsonDecoder`. The other thirteen classes named Encoder or Decoder that the JVM loads are the interfaces and abstract supertypes | `experiments/json-encoder-census/results/`, arm `string` |
| **One `encodeToJsonElement`, anywhere in the process, adds three more concrete encoders** — `JsonTreeEncoder`, `JsonTreeListEncoder` and their `AbstractJsonTreeEncoder` base — plus `TaggedEncoder` and `NamedValueEncoder` in core: 15 loaded classes become 23 | the same log, arm `element`, and the diff it prints |
| The streaming and the sink paths share the one encoder class: `JsonStreamsKt`, the sink entry point, constructs `StreamingJsonEncoder` like `encodeToString` does | `javap` over `org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.11.0!/kotlinx/serialization/json/internal/JsonStreamsKt.class` |
| **Ktor does not cross that line itself.** Across `ktor-serialization-kotlinx-jvm` and `ktor-serialization-kotlinx-json-jvm` 3.5.2, **zero** classes reference `JsonElement` or `encodeToJsonElement`; the converter calls `encodeToString`, `encodeToSink`, `decodeFromString` and `decodeFromSource` | constant-pool search and `javap` over both jars |

**Consequence — RQ6 has two answers and the threshold between them is two.** `TypeProfileWidth` is
**2** (§1.2): C2 records at most two receiver types per call site and treats a third as megamorphic.
A service that only encodes and decodes JSON bodies sees **one** receiver at the shared `Encoder`
sites, which is the best case there is. A service that calls `encodeToJsonElement` once loads three
concrete encoder classes into the process.

**This paragraph used to say those three classes put three receivers on the shared sites and pushed
them over the line in one step. That was wrong, and §1.16 measured it.** Loading a class is not
executing a call site with it: the profile records the receivers a site has *run with*, so a single
tree call in a health endpoint contributes one receiver to the sites it actually reaches and none to
the rest. Sustained mixed traffic — the worst case, not a one-off — settles at **two** receivers,
which `TypeProfileWidth=2` covers. The census below stands; the inference drawn from it does not.

**Consequence — this is the profile pollution RQ7 names, with a name.** The brief lists "profile
pollution in shared generic code" as an RQ7 suspect and gives no example. Here is one that costs a
single line of application code and is invisible in review, because the polluting call need not be
anywhere near the endpoint it slows down.

### 1.6 The continuation machinery, as compiled

| Fact | Where verified |
|---|---|
| `BaseContinuationImpl.resumeWith` is `public final` and calls `protected abstract invokeSuspend(Object)` — one call site whose receivers are every `invokeSuspend` in the process | `javap -p` on `org.jetbrains.kotlin:kotlin-stdlib:2.4.10!/kotlin/coroutines/jvm/internal/BaseContinuationImpl.class` |
| `startCoroutineUninterceptedOrReturn` is **`@InlineOnly`**: `IntrinsicsKt__IntrinsicsJvmKt` carries three `private static final` overloads of it, so nothing outside the file can call one and every caller gets it inlined. `createCoroutineUnintercepted` and `intercepted` are ordinary public static methods. *(This row used to say the class has **no JVM member** for it. It has three; they are private, and a `javap` run without `-p` does not show them — the conclusion was right and the evidence under it was not.)* | `javap -p` on `org.jetbrains.kotlin:kotlin-stdlib:2.4.10!/kotlin/coroutines/intrinsics/IntrinsicsKt__IntrinsicsJvmKt.class` |
| `CoroutineSingletons` is an enum with `COROUTINE_SUSPENDED`, `UNDECIDED`, `RESUMED` | the same jar |
| **The pinned classpath carries 580 `invokeSuspend` implementations** before a line of application code — coroutines 263, `ktor-server-core` 134, `ktor-io` 68, `ktor-utils` 27, stdlib 21, the Netty engine 18, `ktor-http` 15, `exposed-jdbc` 12 | `experiments/method-sizes/results/`, the scan of §1.8 |

**Consequence — RQ2's site is megamorphic by construction, and nothing the application does can
change that.** `resumeWith` is one call site; its receivers are those 580 plus every suspend body
the service adds; `TypeProfileWidth` is 2 (§1.2). There is no configuration, no rewrite and no
volume of application code under which C2 sees a bimorphic profile there. That makes RQ2 a question
about what a *known* megamorphic site costs rather than about whether one exists — and it means the
brief's 1, 2 and 8 receiver arms are a calibration of the microbenchmark, not the measurement. The
service sits at the far end of that gradient and never moves along it.

The brief's benchmark method works — the intrinsic is callable from Kotlin — with the caveat that,
being inline-only, its machinery lands inside the benchmark method's own bytecode and counts against
that method's size.

### 1.7 What this host cannot deliver, measured

The brief's Fixed setup names "one Linux x86-64 machine, fixed CPU governor, load generator on
separate pinned cores". Three of those four were tried here before.

| Fact | Where verified |
|---|---|
| The Linux box is WSL2 and has neither `intel_pstate` nor `cpufreq` under `/sys`: frequency and turbo belong to the Windows host and cannot be fixed from the guest | [research-optimizer](research-optimizer.md) §1.4 |
| **Neither does the dedicated pair.** `bench-a`/`bench-b` are KVM guests with no `cpufreq` and no `intel_pstate` either, so the brief's "fixed CPU governor" is unavailable on every host this study can reach. What the pair buys is the generator being off-host, not a steadier clock (§1.10) | `bench/profile/results/pair-stand-notes.md` |
| `taskset -pc <pid>` moves one thread's affinity, not the process's: 49 of 50 JVM threads stayed on all 20 cores, and every number of the second phase was taken on an unpinned JVM despite a protocol that said otherwise. Pinning on `exec` works | [research-engines](research-engines.md) §1.1, `bench/profile/run.sh` |
| A load generator on the subject's own machine does not merely add noise — it under-feeds the subject. Same binaries, same limits: on a shared 20-core box with the generator on it, a service looked as though it fitted a 256 MiB limit; on a dedicated pair with the generator off-host it survives **1 run in 6** | sborka, §8 of research-memory-limit — see the note below |
| Run-to-run spread for one variant on this stand: ±13 % on rps in the engine A/B, ±15 % in the second phase's, against 2–9 % on µs of CPU per request | [research-engines](research-engines.md) §1.5, [research-optimizer](research-optimizer.md) §1.6 |

The sborka row has no clickable address, and the reason is worth a sentence rather than a
permanently red anchor: docs/research/research-memory-limit.md exists only on the branch
`worktree-brief-c-memory-limit`, which is neither merged into that repository's `main` nor
pushed. Checked on 2026-09-19 — `main` holds only `source-brief-memory-limit.md`. The numbers
above are read out of the branch copy, and §8 there marks itself superseded by §2.5 of the
same document, which is where the six repetitions live.

**Consequence — the macro relevance threshold is below the macro instrument's resolution**
*(deviation from the brief)*. The brief allows a construct's share to be established either by
async-profiler samples or "by the toggle's effect on CPU per request". The second route asks for a
2 % effect from an instrument that moves 13–15 % between identical runs, and the brief's own kill
criterion 3 would end the study over a 5 % spread. The first route survives, because shares by
owner are proportions inside one process and were stable across dirty runs where absolute CPU
differed by 13–17 %. D2.

### 1.8 The size shortlist, for the whole request path rather than for application code

Phase 1 of the brief. The second phase scanned 283 application methods; this scans the 56 405
methods with a body across the 25 artifacts of the pinned stack — Ktor 3.5.2 on Netty,
kotlinx.serialization and coroutines 1.11.0, Exposed 1.4.0 over the PostgreSQL driver 42.7.13 and
HikariCP 7.0.2 — because the request path is mostly framework and a scan of application code alone
prices a twentieth of it.

| Fact | Where verified |
|---|---|
| **691 of 56 405 methods exceed `FreqInlineSize` — 1.23 %** — and 53 of those are class initialisers, which run once and are never candidates for inlining into a request | `experiments/method-sizes/results/`, threshold read from the JVM at report time |
| **Three methods on the whole classpath exceed 8000 bytes, and all three are cold**: two Unicode tables in the driver's shaded `stringprep` (SCRAM authentication, once per connection) and Netty's SPDY class initialiser | the same log's shortlist |
| The densest artifact is the serialisation glue: `ktor-serialization-kotlinx-jvm` has **4 of 36** methods over the threshold — 11.1 % — and content negotiation 4 of 75. By count the leaders are coroutines 104, the driver 101, `ktor-server-core` 71, Exposed 79 across its two artifacts | the per-artifact table |
| **52 `invokeSuspend` bodies exceed the threshold**, and four of them are on the request path by construction: `DefaultEnginePipelineKt$defaultEnginePipeline$1` 1080 b, `DefaultTransformKt$installDefaultTransformations$2` 1044 b, `ResponseConverterKt$convertResponseBody$1$2` 939 b, and Netty's `RequestBodyHandler$job$1` 1122 b | the shortlist, filtered to `invokeSuspend` |
| Exposed's `TransactionsKt.inTopLevelSuspendTransaction` is **1549 bytes** | the same |
| Coroutines' largest bodies are `BufferedChannel.toString`, `toStringDebug` and `checkSegmentStructureInvariants` — debug and invariant code, cold in a service | the same |

**Consequence — the brief's RQ1 suspicion is right and pointed at the wrong code.** It expects
`invokeSuspend` bodies "inflated by inline calls such as `transaction {}`", and names application
code. `transaction {}` does produce one: 1549 bytes in Exposed. But the four suspend bodies that run
on *every* request belong to Ktor and to the Netty engine, and the largest application method the
second phase found was 816 bytes. Under D5 the shortlist is the framework's, and the study's first
RQ1 arm is `DefaultTransformKt$installDefaultTransformations$2` — which is also the method the
second phase already caught building a `KClassImpl.toString` on every `call.receive<T>()`
([research-optimizer](research-optimizer.md) §1.4). Two phases, two instruments, one method.

**Consequence — RQ1's huge-method half is green, and now for a reason.** Nothing on the request
path is within a factor of four of 8000 bytes; the three that exceed it authenticate connections
and speak SPDY. That is a verdict reached by looking rather than by assuming, and it retires half
of RQ1 before the stand exists.

**What this scan cannot say.** Size is static. A method over the threshold is a suspect, not a
cost: only the profile says whether anything calls it, and only the inlining log says whether C2
was ever asked to inline it and refused. `BufferedChannel.toStringDebug` is 941 bytes and will
never appear in a request. The shortlist exists to be intersected with the profile, which is
[B-43](../backlog/B-43-static-scan-across-owners.md)'s second half and B-48's input.

### 1.9 Nine tenths of the size shortlist never runs

§1.8 produced 638 suspects. This intersects them with a CPU profile of the stand, on the engine the
brief pins, so that a suspect becomes a cost or stops being anything. Run on the Linux box —
`bench/` on Netty, `/business`, 45 s of warm-up and 90 s of measurement at 64 connections, JVM
pinned to cores 0–7 and `oha` to 8–15 — 134 870 samples, 91 463 rps, 71 µs of CPU per request.

Two files, and which is committed follows the rule this repository already set for profiles: the
run's `bench/profile/results/netty-jit/summary.md` is versioned, and the 19 MB of collapsed stacks
it was computed from stays on the machine that produced it. What carries the numbers below is
therefore the **join**, `experiments/method-sizes/results/2026-09-19-netty-jit-intersection.log`,
which is committed and which `intersect.py` rebuilds from any later profile of the same shape.

| Fact | Where verified |
|---|---|
| The run reproduces the fourth phase's owner split on Netty: application code **6.0 %** of CPU by owner here against 6.1 % there, on a different build and a different day | `bench/profile/results/netty-jit/summary.md` against [research-engines](research-engines.md) §1.4 |
| **Of 638 oversized methods, 49 appear in the profile.** 192 belong to five artifacts that cannot appear at all, because this stand has no database — Exposed, HikariCP, the driver. Of the 446 that could have run, **397 never do: 89.0 %** | `experiments/method-sizes/results/2026-09-19-netty-jit-intersection.log` |
| **All 49 together own 3.86 % of self samples, and the largest single one owns 0.56 %** — `AbstractChannelHandlerContext.write` at 330 bytes | the same log |
| The artifact where every oversized method runs is the serialisation glue: content negotiation **4 of 4**, `ktor-serialization-kotlinx` 2 of 4 — the same two artifacts §1.8 found densest by share | the same log's per-artifact counts |
| The four "every request" suspend bodies of §1.8 do run, which confirms the shortlist was reading the right code, and their own bodies are cheap: `RequestBodyHandler$job$1` 0.15 % self, `convertResponseBody$1$2` 0.07 % | the same log |
| `NettyHttp1Handler$handleRequest$1$1.invokeSuspend` is 381 bytes, **0.05 % self and 43.85 % stack** — the request-handling root. Size plus a large stack share is a frame, not a cost | the same log |

**Consequence — RQ1's size question is grey by the brief's own vocabulary.** The threshold for red is
2 % of request CPU. No single oversized method on this path owns half a percent of self samples, and
the entire class of them owns 3.86 %. A study that had scanned, shortlisted and stopped would have
reported 638 findings, and 89 % of that list is code nothing executes.

**Consequence — and this is the limit of the intersection.** A method's `self` share is the time
spent in its own body. The cost of a *refused inline* is not that: it is call overhead plus the
optimisation C2 could not do across the boundary, which can be larger or smaller than the body.
So this join **locates**, it does not price. RQ1 survives as a toggle experiment — raise
`FreqInlineSize`, measure µs of CPU per request — and it is now a much smaller one: 49 methods
rather than 638, with four of them named.

**What this run cannot say.** There is no database behind it. Exposed's 79 oversized methods, the
driver's 101 and HikariCP's 11 were never given the chance to appear, and RQ4's territory is
untouched — the whole point of [B-41](../backlog/B-41-jit-stand-data-layer-and-endpoints.md). The
89 % is the honest miss rate; the 92.3 % over the whole shortlist is the one that would have
flattered this section.

**Re-run with a database behind the stand, which is what §1.9 was waiting for.** The first join
used a stub-mode profile, so 192 of the 638 suspects — Exposed, HikariCP and the driver — could not
appear at all, and the 89 % miss rate was honest about that. Joined against four real-mode profiles
instead:

| | stub-mode profile | database-backed profiles |
|---|---|---|
| oversized methods seen at all | 49 | **73** |
| never seen | 89 % | **88.6 %** |
| share of self samples they own together | 3.86 % | **3.48 %** |
| the largest single one | 0.56 % | **0.60 %** — `QueryExecutorImpl.processResults`, 2304 bytes |

**The 192 stopped being unknown and changed nothing.** They do run: `PgResultSet.getObject` at
0.20 % self, `BlockingExecutableKt.executeIn` at 0.11 %, `QueryExecutorImpl.sendBind` at 0.13 %. But
the miss rate moved by half a point and the total share went *down*. Adding a data layer changes
which oversized methods run and not the conclusion drawn from them, which is the comparison
[B-43](../backlog/B-43-static-scan-across-owners.md) asked for rather than a replacement of one
number by another. Log: `experiments/method-sizes/results/2026-09-20-join-with-database.log`.

### 1.10 The stand with a database, on a dedicated pair — and what its ruler turned out to be

B-41 built the data layer; this measures on it. Subject and generator are separate machines
(`bench-a`/`bench-b`, 4 cores and 7 GiB each, private link, RTT 0.74 ms), which is the arrangement
§1.7 says the single-host protocol cannot honestly replace. The service runs the **same
`installDist` bytes** as the WSL runs: the subject has no public IPv4, so it cannot resolve the
settings plugin, and building elsewhere and shipping the distribution turned out to be better
method than a fix — one artefact, two hosts.

| Fact | Where verified |
|---|---|
| The pair is genuinely idle: **steal 0** since boot, 99–100 % idle at rest, nothing but sshd listening | `bench/profile/results/pair-stand-notes.md` |
| **The governor cannot be fixed here either** — these are KVM guests with no `cpufreq` and no `intel_pstate`, exactly like WSL. The pair's advantage is the generator being off-host, not a steadier clock | the same notes |
| **CPU per request is a function of offered rate, not a constant of the code**: 210 µs at 5k rps, 113 at 10k, 92 at 20k, 67 at 40k, 53 at saturation, with the process burning **0.001 cores at idle** | the rate sweep in the same notes |
| **The control holds.** `/plaintext` never touches the data layer, so the two modes must agree there, and they do: **83 µs stub against 82 µs real**, saturation 56 739 against 58 194 rps | `results/pair-{stub,real}-plaintext/summary.md` |
| **The data layer costs about 2.2× the stub at the same offered rate**: `/db/items/{id}` 447 → 995 µs, `/db/items?limit=50` 561 → 1181 µs, `POST /db/items` 647 → 1108 µs | `results/pair-*-{dbitem,dblist,dbpost}/summary.md` |
| **The database is not the limit.** Postgres alone does **15 631 tps** on this box at 0.256 ms average (`pgbench -S`, 4 clients) while the stand's real mode saturates near 3–4.5k with the JVM at 2.2–2.5 of 4 cores | the same notes |
| **19.4 % of the request path's wall time is a spin in the connection pool**: `HikariPool.recycle → ConcurrentBag.requite → Thread.yield`, which is what HikariCP does while anyone is waiting for a connection. Acquisition *wait* is 0.16 %, so the pool's capacity is not the issue — the hand-off is | wall-clock profile, 25 s under load, same notes |
| The database round trip is visible and modest by comparison: `Net.poll` under `VisibleBufferedInputStream.readMore`, 14 % of all wall samples | the same profile |

**The ruler, and it is the most important number here.** Five repeats of one configuration, in one
process, back to back: 3109, 3389, 3952, 4138, 4292 rps — **30 % spread on the median**, and 462 to
613 µs per request. Nothing finer than about 1.5× can be claimed in real mode on this stand.

**Consequence — what survives and what does not.** The 2.2× between stub and real is far outside
that ruler and stands. The `/plaintext` control agreeing to 1 % is inside it, as a control should
be. But the pool sweep (8/16/32/64 → 3223/3099/4675/4410 rps) and the concurrency arms are **single
runs each**, and their differences sit inside the ruler: the same configuration measured 4675 rps in
one sweep and 2641 twenty minutes later. *So the ceiling is not explained.* A first reading of the
spin said "the pool sets it"; that claim is withdrawn — not refuted, unmeasured. The spin itself is
a share taken inside one run rather than a ratio between runs, which is why it survives while the
conclusion drawn from it does not.

**Consequence — the protocol needs one more discard.** The first measured window after a 40-second
warm-up is still the slowest of the five (609 µs against 462–496 for the middle three). Warm-up did
not end where the protocol declared it ended.

**What this does not yet say.** Whether the 2.2× is Exposed's mapping, the driver's round trip, the
`Dispatchers.IO` hop or the hand-off spin is exactly RQ4's question, and it needs the brief's own
control arm — the same query through hand-written JDBC — which is [B-45](../backlog/B-45-rq4-exposed-read-path-in-three-arms.md).
The ceiling has an item of its own now: [B-51](../backlog/B-51-real-mode-ceiling-and-its-ruler.md).

### 1.11 RQ4 answered: Exposed costs 1.3× hand-written JDBC, and the brief's line is 1.5×

Three arms behind one binary, sharing the pool, the dispatcher and the row shape, so that the only
difference between them is the layer: hand-written JDBC with autocommit; the same SQL inside an
explicit `BEGIN`/`COMMIT`; and the shipped Exposed path. All three return identical responses. Two
endpoints, three rounds, arms rotating within each round, the first window after warm-up discarded,
20-second measured windows at a fixed 2000 rps.

| Endpoint | jdbc | jdbc-tx | exposed | exposed / jdbc |
|---|---|---|---|---|
| `/db/items/{id}`, 1 row | 510 µs | 575 µs | 646 µs | **1.27×** |
| `/db/items?limit=50` | 595 µs | 658 µs | 768 µs | **1.29×** |

Spreads across the three rounds: 2–7 %. That is the protocol paying for itself — the same stand
measured a **30 %** spread the day before, on single windows at a floating rate (§1.10).

**The verdict, restated after review — RQ4 is not green on the ratio, and the ratio was never the
deciding half.** This section used to read: green is "CPU per request within 1.5× of hand-written
JDBC", the measured ratio is 1.27–1.29×, therefore green, and the red condition's second half never
comes up. Three things are wrong with that.

*The ratio depends on the rate, and the two measurements straddle the line.* 1.27–1.29× is at a fixed
2000 rps. At saturation, §1.12 measures the same comparison at **1.61×** — 381 µs against 237 on pool
32. The brief's 1.5× falls **between** them, so the same stand answers the same question both ways
depending on offered rate. Calling that "the same order of magnitude", as this document did, papers
over a threshold crossing.

*The line was specified for a mode this stand cannot compare in.* The brief's green reads "within
1.5× of hand-written JDBC for the same query **in stub mode**". Here `-Dbench.data=stub` replaces the
whole data layer, so there is no Exposed-versus-JDBC comparison in stub mode to make; the measurement
had to be real mode. That is a reasonable *deviation from the brief*, but it is one, and it was taken
silently.

*The denominator flatters the ratio.* CPU per request includes Netty, Ktor, serialisation, the
driver and the kernel — a floor common to every arm. So 1.27× is a **lower bound** on what the layer
itself multiplies; the ratio for Exposed alone is larger and is not what was measured.

**What actually decides RQ4 is the red condition's second clause**, which was never tested: above
1.5×, *and* at least a third of the gap traces to failed inlining, megamorphic dispatch or failed
scalar replacement. The decomposition below says the gap is work — a transaction wrapper, query
building, per-column mapping — and not a JIT failure, which is the distinction the brief asks for in
every row. But saying so from a decomposition is an argument, not the test the brief specified, and
**RQ4 therefore stood as amber** until the clause was tested. §1.22 tests it: about a tenth of the gap
traces to the three mechanisms, against a required third, so **RQ4 is not red** — and the ratio stays
rate-dependent, which is why "green" as the brief words it is not a stable answer either.

**The decomposition is worth more than the verdict**, because it says where the 30 % sits:

| Layer | Cost | Shape |
|---|---|---|
| the transaction wrapper | **~64 µs per request** | flat — 65 µs on one row, 63 µs on fifty |
| Exposed's fixed part | **~70 µs per request** | query building, transaction manager, statement setup |
| Exposed's mapping | **0.76 µs per row** over hand-written, ≈ **0.151 µs per column** | grows with rows: +71 µs at one row, +110 µs at fifty |

**Consequence — §1.4's suspicion was right in mechanism and small in size.** The per-column
`HashMap` lookup in `getExpressionIndex` plus the `valueFromDB` dispatch are real and they are what
the per-row term measures, but they cost about 0.15 µs a column. On a fifty-row page with five
columns that is 38 µs against a 768 µs request — 5 %. A study that had found the mechanism and
stopped would have reported a defect; the number turns it into a footnote.

**Consequence — the biggest single line is not Exposed at all.** Wrapping the same SQL in an
explicit transaction costs 64 µs, as much as everything Exposed adds on a single-row read. That is
not a JIT question and not an Exposed question; by the brief's own distinction it is library cost
and is recorded and left alone.

**What this does not answer.** The brief also asks for a typed row holder against `Array<Any?>` in
a microbenchmark. That arm cannot be staged honestly at service level — Exposed exposes no
index-based row access — and it needs JMH, which this phase does not have yet. The per-row term
above bounds what it could ever show: 0.76 µs a row is the whole of what a perfect holder could win.

**A caution about reading these beside §1.10.** Those numbers were taken at a different pool size
and a floating rate; the same `exposed` arm reads 995 µs there and 646 µs here. Absolute
microseconds do not survive between sweeps on this stand, ratios do, and the ratios here were all
taken inside one sweep against each other.

### 1.12 The ceiling is two cores of four, and it is neither the pool nor Exposed

§1.10 left the real-mode ceiling unexplained and §1.11 measured on top of it anyway. This is the
repeat-backed sweep B-51 asked for: four configurations, three rounds, rotating within each round,
the first window after warm-up discarded. Spreads 3–10 %, against the 30 % single windows gave.

| Arm | Pool | rps (median) | spread | cores | µs/req | p50 |
|---|---|---|---|---|---|---|
| exposed | 16 | 5286 | 3 % | 1.98 | 375 | 43.0 ms |
| exposed | 32 | 5069 | 10 % | 1.93 | 381 | 44.2 ms |
| exposed | 64 | 4595 | 4 % | 1.93 | 420 | 53.5 ms |
| **jdbc** | 32 | **8488** | 8 % | 2.01 | **237** | 26.5 ms |

**The pool is not the ceiling, and now that is measured rather than guessed.** More connections are
slightly *worse*, monotonically: 5286 → 5069 → 4595 across 16, 32, 64. The "step at 16→32" that
§1.10 saw was noise between single runs, and it is now retired.

**Exposed is not the ceiling either, though it is the cost.** Hand-written JDBC through the same
pool and the same dispatcher runs **1.67× more requests** — and spends **1.61× less CPU on each**.
Those two ratios agreeing is the point: both arms stop at the *same wall* and differ only in what a
request costs to get through it.

**The wall is ~2.0 of 4 cores, in every configuration measured.** Pool 16, 32, 64; Exposed and raw
JDBC; 4595 to 8488 rps — cores stay at 1.93–2.01. Throughput is then simply two cores divided by
the price of a request, which is why the two ratios match.

**Whether stub mode has the same wall is not measured here, and a number that claimed it did has
been withdrawn.** This section used to say stub mode reached "3.51 of 4 cores" in the calibration
sweep of §1.10. That figure is in no results file in this repository, §1.10 does not contain it, and
the highest core count any run recorded is 3.87 — in `probe-run`, a five-second probe, which measures
warm-up. What the fixed-rate pairs do show runs the other way and does not settle it either: stub
takes **fewer** cores than real at the same offered rate (0.95/1.02/1.06 against 2.12/2.15/1.81 on
the three database endpoints), which is what doing less work per request looks like, not a ceiling.
The wall
arrives with the data path, and what the data path adds is the `withContext(Dispatchers.IO)` hop.

**Consequence — the hypothesis is named and testable, and the earlier one is dead.** The spin in
`ConcurrentBag.requite` (§1.10) is real but cannot be the ceiling: it lives in the pool, and the
ceiling does not move with the pool. The live hypothesis is the IO dispatcher, and it has a lever
the fourth phase already used — `kotlinx.coroutines.io.parallelism`. If the wall moves with it, it
is the dispatcher; if it does not, this hypothesis dies as the last one did and the section says so.

**Consequence for §1.11's verdict — it stands, and for a better reason than before.** RQ4's 1.27–
1.29× was measured at a fixed 2000 rps, far below every ceiling here, so it was never a comparison
of walls. The ceiling sweep independently puts the same gap at 1.61× in CPU per request *at
saturation* — a different rate, a different protocol, the same direction and the same rough size.

### 1.13 The ceiling is the machine: the database and the kernel own the other half

Three hypotheses about the JVM failed to explain the two-core wall — the pool, the repository arm,
the IO dispatcher. The fourth candidate was the one thing none of them looked at: `cost:` counts the
JVM's own `utime+stime`, and **PostgreSQL runs on the same box**.

| Measured under load, 11 398 rps — see the caveat below | cores |
|---|---|
| the JVM | 1.89 |
| PostgreSQL | 1.24 |
| **the whole machine** | **3.93 of 4** |
| so: kernel, network stack, everything else | 0.80 |

**The box is full.** The service stops at two cores because two cores is what is left after the
database takes a third of a four-core machine and the network stack takes a fifth. Stub mode reached
3.51 cores on the same binary and the same machine precisely because Postgres had nothing to do.

**The brief's own mitigation does not work at this core count**, and that is a *deviation from the
brief*. Its threats section says the same-host database "is pinned to its own cores so that it does
not inflate JVM CPU per request". Pinned — Postgres to core 0, the JVM to 1–3 — throughput **fell**
from 11 398 to 9 471 rps: pinning did not give the JVM a third core, it throttled the database from
1.24 to 0.96 cores and made it the limit. Pinning separates competitors when the machine has spare
capacity. Here there is none to partition.

**The rps figure here is not comparable with §1.12, and its arm was not recorded.** 11 398 rps
appears in the results file, but the run's endpoint and repository arm were never written down beside
it; the only handle on which arm it was is its cost fingerprint, 166 µs/request, which matches the
`io.parallelism=16` arm of the same sweep. §1.12's table tops out at 8488 rps on different arms, so
the two must not be read against each other. **What this section actually rests on is the core
split** — 1.89 + 1.24 + 0.80 = 3.93 of 4 — and that is an accounting of the whole machine which does
not depend on the throughput number at all.

| Fact | Where verified |
|---|---|
| Whole machine 3.93 of 4 under load; JVM 1.89, Postgres 1.24, remainder 0.80 | `bench/profile/results/pair-ceiling-mechanism.md` |
| Pinning Postgres to one core lowers throughput 11 398 → 9 471 rps and leaves the JVM at 1.99 cores | the same file |
| `kotlinx.coroutines.io.parallelism` moves the **price** of a request but not the wall: 245 µs at 4, **166 µs at 16**, 210 at the default 64, 235 at 128 — cores 1.77–2.02 throughout | `bench/profile/results/pair-ceiling-mechanism.md`, two rounds each |

**Consequence — a side result worth more than the ceiling.** The default `Dispatchers.IO` size of 64
is **27 % more expensive per request** than 16 on this four-core box, and 16 is the cheapest of the
five sizes tried. That is a tuning fact about every Ktor service that hops to `Dispatchers.IO`, it
is measured with repeats, and it is not a JIT finding — by the brief's own distinction it is library
cost, recorded and left alone.

**Consequence — what the ceiling numbers are about.** Every saturation number on this stand
describes the four-core box, not the stack. So RQ4's verdict (§1.11) is the one to trust: it was
taken at a fixed 2000 rps, far below saturation, where the arms differ only in their own cost. The
rule this leaves for the rest of the phase: **construct verdicts come from fixed-rate runs below
saturation; saturation runs describe the stand.**

**Consequence — B-51 closes with an answer and a cost.** The stand cannot be fixed by pinning; it
would need the database on a third machine, and this pair has two. The honest statement in the
write-up is that the real-mode ceiling is the machine, and every real-mode absolute carries it.

### 1.14 JMH: the controls pass, RQ2 gets its number, and two benchmarks of mine were wrong

The phase now has a microbenchmark harness — a build of its own, JMH 1.37, run on the idle half of
the pair (4 cores, steal 0, nothing else running). The governor cannot be fixed there either, which
is why every run is three forks rather than one: three JIT histories instead of one.

**The calibration controls pass, so the chain can be believed.** B-44, and by the brief's kill
criterion 2 nothing below it would have counted otherwise. Each control is a pair — the Kotlin
construct against the hand-written equivalent it should compile to — and green means the pair is
within its intervals.

| Pair | Kotlin | equivalent | verdict |
|---|---|---|---|
| `inline` with a lambda | 76.05 ± 2.49 ns | hand-written loop 74.90 ± 2.94 | green |
| `Intrinsics` parameter check | non-null 1.670 ± 0.062 | nullable 1.863 ± 0.397 | green |
| `when` over a sealed hierarchy | 1999 ± 59 | int switch 1917 ± 160 | green |
| `for` over a range | 393.5 ± 3.9 | `while` 435.2 ± 38.2 | green |
| data class `copy` | 5.354 ± 0.119 | plain class 5.593 ± 0.151 | green |
| **the known-order pair (D8)** | base 179.7 | the same plus one multiply 269.5 | **order correct** |

**RQ2 has its micro number, and it is the brief's own toggle.** One call site, the same work per
call, only the number of implementing classes behind it changing:

| receivers | ns per call | against monomorphic |
|---|---|---|
| 1 | 0.755 | — |
| 2 | 1.292 | 1.7× |
| **8** | **6.715** | **8.9×** |

The break is between 2 and 8, which is where `TypeProfileWidth = 2` says it should be (§1.2). The
service's own resume site carries 580 receivers (§1.6), so the `mega` arm is the one that stands for
it: **a megamorphic call on this path costs about 6 ns against 0.76 ns monomorphic.** Whether that
matters is a macro question the microbenchmark cannot answer — 6 ns is 0.004 % of a 166 µs request,
so it takes hundreds of such calls per request to reach the brief's 2 % line, and how many there are
is a profile question.

**Two of my own benchmarks were wrong, and both were caught by rules this document already had.**

*RQ3 measured folding, not suspending.* `plainChain` came out at **0.701 ns** — about two cycles,
which is the blackhole and nothing else. The input was the literal `7`, so the plain chain folded to
a constant while the suspend chain did not, and the pair compared folding against not-folding. The
input is now a `@Volatile` field the JIT cannot see through, and RQ3 is **unanswered** until that
rerun lands rather than answered at 5.6×.

*The "indexed iteration loses vectorisation" claim is dead.* It came out of the controls:
`handWrittenLoop` summed 1024 ints in 74.9 ns while `forOverRange` took 393.5 — five times longer
for strictly more work. Isolating it refutes the reading: `direct` 406.7, `indexedOverIndices`
396.1, `indexedOverSize` 396.9 — indexing changes nothing. What differs is the **multiply**:
`directTimesTwo` is 77.3 ns, and it is the arm doing *more* work. Reproduced in two independent
benchmark classes.

By D8's own rule an impossible ordering means the stand is suspect for that pair and the stand is
what gets fixed, not the number. So this is recorded as an open anomaly rather than a finding, and
what it needs is the brief's own step 3 — `-prof perfasm` on the generated code. Item
[B-52](../backlog/B-52-multiply-makes-the-loop-faster.md).

### 1.15 RQ3: nothing survives — and the 16 B/op was the measurement

**This section previously concluded the opposite**, and the author of the brief is the one who
caught it. It read: "the boxed primitive is removed, the continuation is not", on 16 B/op measured
across three arms. The objection was arithmetic and decisive — a continuation cannot weigh 16 bytes.
`javap -p` on the generated class settles it: `Continuations$hoisted$1` carries `completion` from
`BaseContinuationImpl`, `_context` and `intercepted` from `ContinuationImpl`, `arity` from
`SuspendLambda`, and its own `label` and `this$0` — a 12-byte header and six fields, 36 bytes padded
to **40**. The smallest continuation in the chain is 32. A 16-byte object is a header and one
four-byte field, which is a `java.lang.Integer`.

So the claim was tested the way it should have been the first time: by naming the object.

| | ns/op | B/op |
|---|---|---|
| `plainChain` | 0.917 ± 0.028 | ≈ 0 |
| `suspendReturningIntUnboxed` — one suspend call, result used as `Int` | **0.911 ± 0.035** | **≈ 0** |
| `suspendChainHoistedInt` — three-level suspend chain, result used as `Int` | 1.826 ± 0.056 | ≈ 0 |
| `suspendChainHoisted` — the same chain, result consumed as `Any` | 4.163 ± 0.091 | 16.000 ± 0.001 |
| `suspendReturningInt` — the same call, result consumed as `Any` | 3.846 ± 0.092 | 16.000 ± 0.001 |
| `allocatesOneInteger` — **the unit control** | 4.030 ± 0.060 | **16.000 ± 0.001** |
| `suspendChainHoisted` with `-XX:-DoEscapeAnalysis` | 37.183 ± 1.960 | **168.000 ± 0.001** |

**The continuations are scalar-replaced.** Turning escape analysis off takes the same arm from 16 to
**168 B/op**: the 152 bytes that appear are the three state machines this chain builds (40 + 32 + 32)
and the intermediate boxes between them. With EA on, none of it is allocated. That is the direct
answer to the brief's RQ3, and it is the answer EA is supposed to give.

**The 16 bytes that remained were the blackhole's.** `startCoroutineUninterceptedOrReturn` is
declared to return `Any?`, so the fast-path value arrives boxed; real Kotlin unboxes it on the next
instruction, while `bh.consume(r)` takes an `Object` and forces the box to escape. Consume the same
result as an `Int` and the allocation goes to zero — and `suspendReturningIntUnboxed` at **0.911 ns
against `plainChain`'s 0.917** is the whole cost of a non-suspending suspend function: nothing.

**The unit was measured, not inferred.** `allocatesOneInteger` allocates exactly one escaping
`Integer` and nothing else, and reads 16.000 B/op. Without that control, "16" was a number whose
meaning had been guessed — which is precisely how the first reading went wrong.

**A finding that fell out of the correction: Kotlin's coroutine boxing helper does not use the
`Integer` cache.** An arm built to separate the box from the machinery — the same chain on a value
of 27, well inside the cache — still allocated 16 B/op. `javap -c` on
`kotlin/coroutines/jvm/internal/Boxing` shows why: `boxInt` compiles to `new Integer(i)`, not
`Integer.valueOf(i)`. The cache is never consulted. So the earlier reasoning about staying outside
the cache range was beside the point from the start, and where a suspend-returned primitive genuinely
does escape, it allocates every time regardless of its value.

**Verdict: RQ3 is green.** The brief's green condition is "B/op of a non-suspending suspend chain
within 10 % of the same chain as plain calls". Both are zero, and the time difference for a single
suspend call is 0.911 against 0.917 ns. The three-level chain costs about 0.9 ns more than three
plain calls, which is real and is 0.0001 % of a 646 µs request.

**Four versions of this benchmark were wrong before this one, and each looked reasonable.**

1. *It compared folding against not-folding.* The input was a literal, so the plain chain folded to a
   constant — 0.701 ns, about two cycles, which is the blackhole and nothing else. Fixed with a
   `@Volatile` field.
2. *It could not say whose allocation it measured.* The `suspend { }` literal sat inside the
   benchmark method and captured the receiver, so a fresh lambda was allocated per call. The hoisted
   arm was added beside it rather than replacing it.
3. *The boxing arm boxed nothing.* It summed 7 and 11; 18 is inside the `Integer` cache. Moving the
   input to 1000 was thought to be the fix — and, per the `Boxing.boxInt` finding above, changed
   nothing at all, because that path never asks the cache.
4. *The allocation it did measure belonged to the harness, and its unit had been inferred rather than
   controlled.* This is the one that produced a published verdict. Two arms agreeing at 16 B/op was
   read as confirmation; both were agreeing about the blackhole.

The first three were caught by asking what else the arms differed in. The fourth was caught by
someone else doing arithmetic on a field layout — which is the argument for sending a research
document to the person who commissioned it before believing its table.

### 1.16 RQ6: two receivers is still two, and the profile width is two

§1.5 counted which encoders a process loads and drew a consequence from it. The consequence was
wrong, and pricing it took three arms to find out — which is the useful part.

**A one-off tree call costs nothing.** `EncoderClean` 25.427 ± 0.972 µs against `EncoderPolluted`
25.386 ± 1.035, with allocation identical to three decimals — and the manipulation is verified, not
assumed: under `-verbose:class` the clean arm loads 2 encoder classes and the polluted one 7.

**Because loading a class is not polluting a profile.** A type profile is kept per call site and
records the receivers that site has *executed with*. One `encodeToJsonElement` in a setup is one
pass out of millions; C2 treats a receiver at that frequency as an outlier and emits a guard with an
uncommon trap, not a megamorphic site.

**Sustained mixed traffic does move the profile, and the throughput arm could not show it.**
`EncoderMixed` is 17 % slower than a double-clean control but allocates **3.4× more** — 67 624 B/op
against 19 776 — because `encodeToJsonElement` builds a whole `JsonElement` tree. The extra 8.5 µs
is that tree's work, and no control built out of `encodeToString` can subtract it. That arm is
recorded as unusable rather than quoted.

**So the question went to the instrument the brief names for it — step 3, the inlining log.** The
type profiles at the shared sites, read out of `-XX:+PrintInlining`:

| arm | site | receivers |
|---|---|---|
| clean | encoder call | `StreamingJsonEncoder` **10618/10618** and **8321/8321** — 100 % |
| mixed | the same | `StreamingJsonEncoder` **4989/9962**, `JsonTreeEncoder` **4973/9962** — 50/50 |
| mixed | another | `JsonTreeListEncoder` **5681/11361**, `JsonTreeEncoder` **5680/11361** |

**Verdict: RQ6 is green, and it stays green under sustained mixing.** A JSON-only service is
monomorphic at these sites. Mixed traffic makes them **bimorphic** — and `TypeProfileWidth` is 2
(§1.2), so two receivers is exactly what C2 still profiles and can inline behind a two-way guard.
The brief's red condition — "virtual or interface calls remain at those sites and meet both
thresholds" — is not reached.

**The correction to §1.5, stated plainly.** That section said one `encodeToJsonElement` anywhere
"takes every shared `Encoder` site from one receiver to three, crossing the type-profile width in a
single line". The census behind it is right; the consequence is not. Loading three encoder classes
does not put three receivers on a site — reaching three requires three of them in *sustained* use,
and the tree path in practice contributes one (`JsonTreeEncoder`) at a time per site. One line of
application code buys bimorphism, which C2 handles, not megamorphism, which it does not.

### 1.17 RQ1's lever moves nothing, and that closes it green

§1.8 found 638 methods over `FreqInlineSize` on the request path; §1.9 narrowed that to the **49
that actually run**, owning 3.86 % of self samples between them with the largest at 0.56 %. That
bounded what the dial could possibly be worth. This pulls it.

Protocol as in §1.11, the one that gives 2–7 % spreads instead of 30: a fixed offered rate of 2000
rps, arms rotated within each round, the first window after warm-up discarded, three measured
rounds, on the fifty-row endpoint where the serialisation glue lives.

| `FreqInlineSize` | µs/req (median) | rounds | spread |
|---|---|---|---|
| **325**, the default | 607 | 587, 607, 613 | 4.3 % |
| **2000**, past every *bytecode size* on this path | 599 | 589, 599, 606 | 2.8 % |

**1.3 % apart, inside a ruler of 2.8–4.3 %.** Stated the way the document's own Risk 3 requires:
the effect is **below about 4 %, at n = 3**. That is not zero, and this section used to write it as
though it were.

**The lever does engage, and that had to be checked separately.** A null result from a dial nobody
proved was connected is the §1.3 failure over again — a check that never found its subject. The
brief's author raised exactly this, and `FreqInlineSize` is genuinely not the only gate:
`InlineSmallCode=2500`, `MaxInlineSize=35` and `MaxInlineLevel=15` each refuse methods the size dial
has already let through. So the same workload was run again under `-XX:+PrintInlining`, timing
nothing and counting refusals:

| refusal, and the flag it belongs to | 325 | 2000 |
|---|---|---|
| **`hot method too big`** — `FreqInlineSize` itself | **155** | **3** |
| `too big` — `MaxInlineSize`, untouched by the lever | 731 | 732 |
| `already compiled into a big method` — `InlineSmallCode` | 324 | **453** |
| distinct methods refused as too big | 165 | 123 |

**The dial moved its own gate almost completely — 155 refusals to 3 — and a second gate took part of
the slack**, `InlineSmallCode` refusals rising 324 → 453. So the 1.3 % is a real null over a lever
that demonstrably fired, not an artefact of nothing happening; and the earlier phrase "past every
method that runs here" was wrong, because passing the size threshold is not the same as being
inlined.

**Verdict: RQ1 is green on both halves.** The brief's red needs either a hot method left uncompiled
or "raising `FreqInlineSize` meets both thresholds" — 10 % on the micro measure and 2 % of request
CPU on the macro. Neither is approached. The huge-method half was already green by inspection
(§1.8): nothing on the path is within a factor of four of 8000 bytes, and the three methods that
exceed it authenticate connections and speak SPDY.

**Consequence — the size threshold is real, visible, and worth nothing here.** Every step of the
chain found its subject: 638 methods are genuinely over the threshold, 49 of them genuinely run,
their refusals genuinely appear in the compilation log, and raising the dial genuinely removes those
refusals. And the toggle that would pay for fixing them returns 1.3 %, inside the ruler. That is the difference between a mechanism and a cost, and it is the distinction
the brief asks for in every row.

### 1.18 RQ5 measured, on both halves: three patterns are free, two are not

D5 rescoped RQ5 from application code to every owner on the path, which splits it in two: what each
pattern costs, and how often it occurs in whose code. Both halves are now done.

**The price.** Each pattern against the hand-written equivalent it is supposed to compile to, same
JMH protocol and same host as the controls of §1.14, so the chain's resolution is the known one:
differences under about 0.3 ns, or 5 % at that scale, are not distinguishable.

| pattern | ns/op | B/op | its control | verdict |
|---|---|---|---|---|
| value class through a generic | 0.866 ± 0.029 | ≈ 0 | raw `Int` through the same generic, 0.865 | **free** |
| value class through a nullable | 0.866 ± 0.023 | ≈ 0 | used directly, 0.848 | **free** |
| `$default` synthetic | 0.997 ± 0.148 | ≈ 0 | explicit arguments, 0.925 | **free** |
| capturing lambda, non-`inline` callee | 302.910 ± 16.563 | ≈ 0 | hand-written loop, 305.472 | **free** |
| `by lazy` | 1.995 ± 0.293 | ≈ 0 | plain field, 0.948 | +1.0 ns |
| **`Delegates.observable`** | **14.289 ± 0.725** | **16** | plain field, 0.948 | **15×** |
| **eager collection chain** | **4592 ± 155** | **7184** | hand-written loop, 1216 ± 48 | **3.8×** |
| `Sequence` chain | 1750 ± 99 | 4096 | the same loop | 1.4× |

Three of the four patterns the brief names are free, and not marginally: a value class boxed through
a generic or a nullable is **0 B/op** — C2 removes the box entirely — and reads within 0.02 ns of the
raw `Int`. A capturing lambda passed to a non-`inline` function allocates nothing and runs the same
speed as the loop it replaces.

**The two that cost are not on the brief's list.** `Delegates.observable` is 13.3 ns and one box per
write, because every write goes through `ReadWriteProperty.setValue` with the old and new values
boxed for the callback. And an eager `map { }.filter { }.sum()` over 256 elements costs **3.4 µs and
7184 bytes** more than the same loop written out — two intermediate `ArrayList`s and 256 boxed
`Integer`s. Neither is a JIT failure: both are work the code asked for, and the distinction is the
one the brief asks for in every row.

**The `Sequence` result is the reverse of how the two are usually ranked, and the bytecode says
why.** `List.map` and `List.filter` are `inline` in the standard library and leave no call site at
all; `Sequence.map` and `Sequence.filter` are not inline and leave one call per stage. Yet the lazy
chain is **2.6× cheaper** here, because what dominates is not dispatch but the intermediate lists the
eager form materialises. A census that counted call sites and stopped there would have ranked them
the other way round — which is why the count below is reported beside the price and not instead of it.

**The count.** `experiments/codegen-census/patterns.py` walks the Code attribute of every class on
the same pinned classpath the size scan reads, counting invoke instructions rather than constant-pool
entries — a class calling `foo$default` ten times holds one pool entry, so counting entries would
understate every pattern by however much it is reused.

| pattern | total | where it concentrates |
|---|---|---|
| null-check intrinsic | 17 950 | stdlib 9180, exposed-core 3308, ktor-server-core 1332 |
| `$default` synthetic | 1 363 | exposed-core 299, coroutines 197, ktor-server-core 186 |
| eager collection op | 1 832 | stdlib 793, exposed-core 406, ktor-server-core 165, ktor-http 165 |
| value class boxed | 1 175 | stdlib 1058 — 90 % of all of them |
| capturing lambda classes | 289 | coroutines 103, ktor-server-core 93 |
| `Sequence` op | 238 | stdlib 184 |
| delegated `getValue`/`setValue` | 168 | exposed-core 58, ktor-http 26, ktor-server-core 17 |

**Roughly two fifths of the request path cannot have any of these patterns at all.** Netty, the
PostgreSQL driver and HikariCP are **2567 of 6700 classes** and score zero in every column, because
they are Java. RQ5's subject is smaller than the path it lives on before a single measurement.

**The most common pattern is the one measured to be free.** 17 950 null-check intrinsics is an order
of magnitude more than everything else combined, and the controls of §1.14 put `checkedParam` at
1.670 ns against `uncheckedParam` at 1.863 — indistinguishable, with the checked arm nominally
faster.

**Verdict: RQ5 is green.** Every construct the brief named is free or within the ruler. The two that
are not free — `Delegates.observable` and eager collection chains — occur 168 and 1832 times
respectively, mostly inside Exposed and Ktor, and §1.20 bounds both by what a request allocates at
all: **0.18 % and 0.24 % of request CPU**. At the time this section was written those bounds were a
profile question; they are now a measurement.

| Fact | Where verified |
|---|---|
| Pattern prices, 3 forks × 5×2 s on bench-a, JDK 25.0.4 | `microbench/results-rq5.md`, `microbench/src/jmh/kotlin/micro/Codegen.kt` |
| Pattern counts over the pinned stack | `experiments/codegen-census/results/2026-09-19-230019-ktor-3.5.2-exposed-1.4.0.log` |
| The counter agrees with `javap` on an independent artifact | exposed-core: 299 `$default` against javap's 300, 56 `$delegate` fields against 56 |
| The scanned stack is the one the stand runs | `experiments/stack.sh` refuses to scan unless every pin matches `bench/profile/results/dist-manifest.txt`, taken from the stand's own `installDist` |

### 1.19 RQ2's number was taken through the wrong dispatch table

The brief's author objected that `mega` sends its eight receivers through an **interface** method,
which is an itable lookup, while the site the number was carried to — `BaseContinuationImpl.resumeWith`
calling the abstract `invokeSuspend` — is a virtual call on a class, which is a vtable lookup. The
objection is right, and the two are not priced the same.

| dispatch | monomorphic | eight receivers | ratio |
|---|---|---|---|
| interface (itable), as first measured | 0.755 | 6.715 | **8.9×** |
| abstract class (vtable), the shape `invokeSuspend` actually has | 0.693 | **4.006** | **5.8×** |

So the figure RQ2 should carry is **4.0 ns per megamorphic `invokeSuspend` call against 0.69
monomorphic**, not 6.7 against 0.76. Both are far past the brief's 10 % micro line; the correction
matters for the macro arithmetic, which is what the question now turns on.

**And the macro arithmetic closes RQ2 without a profile.** At 3.3 ns of excess per call, reaching 2 %
of a 646 µs request needs about **3900** megamorphic `invokeSuspend` calls in one request. The brief's
author makes the sharper point: `resumeWith` runs only on a genuine *resumption*, and a request has
single-digit to low-tens of those — the IO hop, the socket read, the socket write. Two numbers three
orders of magnitude apart do not need a measurement to be ordered.

### 1.20 The three macro shares, measured instead of argued

RQ2, RQ3 and RQ5's two off-list patterns were all closed by the same reasoning: the count a request
would need to reach 2 % is orders of magnitude above the count it makes. That reasoning is sound and
it is not a measurement, and §6 listed replacing it as the first thing left to do. This does it, from
two profiles of the same stand — the CPU profiles already committed with the pair runs, and a new
allocation census.

**RQ2: 0.5–0.9 % of request CPU, against a 2 % line.** The brief names two sites and asks for both
together. Taking *self* samples at each — the callees' cost belongs to the callees, not to the
dispatch:

| | `BaseContinuationImpl.resumeWith` | `io.ktor.util.pipeline.*` | together |
|---|---|---|---|
| dbitem | 0.23 % | 0.49 % | **0.72 %** |
| dblist | 0.16 % | 0.36 % | **0.52 %** |
| dbpost | 0.42 % | 0.45 % | **0.87 %** |

Self samples at `resumeWith` include the whole body, not just the vtable lookup, so this is an upper
bound on what the megamorphic dispatch costs. An upper bound under the threshold is a clean green.

**RQ3: the machinery is a quarter of what a request allocates, and that is worth 0.3 % of its CPU.**
§1.15 showed escape analysis removes the continuation on the path where a suspend function does not
suspend. A real request *does* suspend — the IO hop, the socket read, the socket write — so the
question the macro half asks is what survives there. The allocation census answers it:

| bucket | dbitem | dblist | dbpost |
|---|---|---|---|
| total allocation | **23 434 B/req** | **74 953 B/req** | **30 741 B/req** |
| coroutine machinery | 5894 B — 25.2 % | 5726 B — 7.6 % | 7042 B — 22.9 % |
| boxing | 373 B — 1.6 % | 2604 B — 3.5 % | 424 B — 1.4 % |
| **RQ3's bucket together** | **26.7 %** | **11.1 %** | **24.3 %** |

So continuations are not free on the real path, and 5.7–7.0 KB per request is a real number. The
brief's red condition asks what that costs: "continuation plus boxing allocations reach 2 % of
request CPU". **All garbage collection on this stand is 1.17–1.23 % of CPU.** Even attributing GC
strictly in proportion to bytes, RQ3's bucket is worth **0.13–0.33 % of request CPU** — and the
allocation itself is a TLAB pointer bump already counted in the mutator frames. The red condition
cannot be met by a bucket whose entire collector costs half of it.

**RQ5's two off-list patterns are bounded by what the request allocates at all.** An eager
`map{}.filter{}.sum()` over 256 elements costs 3.4 µs and 7184 B over the hand-written loop (§1.18).
The largest `collections` bucket measured is 5651 B/req, so there is room for **at most 0.79 such
chains per request** — 2.7 µs, or **0.24 %** of dbpost's 1108 µs. `Delegates.observable` costs 13.3 ns
and one 16-byte box per write; the largest boxing bucket is 2604 B/req, so **at most 163 writes** even
if every box in the request were one — 2.2 µs, or **0.18 %** of dblist's 1181 µs. Neither can reach
2 % on this stand without allocating more than the whole request does.

**Side result — C2's own threads cost 4.9–6.1 % of CPU here, on a saturated four-core box.** That is
four to five times the collector, on a stand running flat out where compilation should long since
have settled. It is the same quantity Open question 3 found at 61 % in a one-core container at 50
rps, measured at the other end of the range, and it is larger than every construct in the brief's
list put together.

**What this section does not claim.** Two things. The allocation census warmed for **40 s**, and the
warm-up measurement that came later ([B-42](../backlog/B-42-warmup-gate-on-printcompilation.md)) put
the quiet point at 90 s of load — so these numbers are taken slightly early, while some compilation
is still going. The bias runs one way, towards *more* allocation and CPU than steady state, and every
answer here lands an order of magnitude below its threshold, so it does not move a verdict; it would
if any of them were near the line. And the census ran uncapped at 4709/3186/2992 rps while
the CPU-per-request denominators come from the pair runs at 2131/1818/1635. Allocation per request is
robust across that gap in a way CPU per request is not (§1.10: 210 µs at 5k rps, 53 at saturation),
so the byte counts are solid and the percentages of CPU are approximate — which is enough when the
answers land an order of magnitude below the line, and would not be if they were near it.

| Fact | Where verified |
|---|---|
| Allocation per request and its buckets, three endpoints | `bench/profile/results/alloc-census.md`, `bench/profile/alloc-census.sh` |
| Self samples at the two RQ2 sites; GC and JIT share of CPU | the committed `pair-real-db*.cpu.collapsed` profiles |

### 1.21 RQ7, the only question never measured: green on exceptions, red on a threshold nothing meets

RQ7 asks whether steady state is stable. Its green is "under 1 deoptimisation per minute after
warmup, and exception construction under 2 % of request CPU"; its red is "a deoptimisation recurring
at the same site, or exception construction at 2 % or more". The two halves need different readings —
one is a rate, the other a shape — and both were corrupted by the instrument before they were read.

**The instrument deoptimises the service, twice per recording.** Raw, the runner reports 33.7, 31.3
and 25.0 deoptimisations per minute, which would be red thirty times over. Bucketed by ten seconds
the shape is not a rate at all:

| | first 10 s | the middle 160 s | last 10 s |
|---|---|---|---|
| dbitem | **59** | 15 | **16** |
| dblist | **62** | 6 | **17** |
| dbpost | **37** | 14 | **5** |

`JFR.start` with `settings=profile` enables instrumentation that forces recompilation, and `JFR.stop`
undoes it. Filtering events whose stack is JFR's own removes 9–19 of them; the rest are the service's
own methods being deoptimised *because* the recording started. **Only the middle is steady state.**

**Deoptimisation, measured properly: 2.25–5.62 per minute, or 11–38 per million requests.** An order
of magnitude below the raw figure and still above the brief's "under 1 per minute". Actions split
`maybe_recompile` / `reinterpret` roughly evenly, and the reasons are ordinary speculation failures —
`speculate_class_check`, `unstable_if`.

**One site genuinely recurs, and the brief's red names it.** Most repeated "sites" are bursts inside a
single millisecond — one deoptimisation event recorded several times, not a site returning. Sorting by
interval instead of by count leaves exactly one: `kotlinx.coroutines.scheduling.CoroutineScheduler$Worker.tryPark()@40`,
four times at **31 s, 26 s, 16 s** apart, reason `unstable_if`, action `reinterpret`. The scheduler's
park decision is bimodal by construction, C2 speculates it will not park, and it periodically does.

**So RQ7's deoptimisation half is red by the letter of the criterion and worth nothing by the
measurement** — four events in 160 seconds. That is a *deviation from the brief* worth stating plainly:
**"under 1 deoptimisation per minute" appears to be a threshold no healthy JVM under load meets**, and
"a deoptimisation recurring at the same site" fires on ordinary adaptive reprofiling. A criterion that
a well-behaved service fails does not separate well-behaved services from badly behaved ones.

**The exception half needed a different instrument than the brief's, for the third time in this
phase.** `jdk.JavaExceptionThrow` reported 51 607–52 406 throws per 180-second window — nearly the
same count on endpoints running at 4788, 2974 and 1768 rps, which is not a property of the load. It is
**throttled at 300/s in `profile.jfc`**: 300 × 180 = 54 000, and the recording was sitting on the
ceiling. `jdk.ExceptionStatistics` carries the uncapped counter, and it says something else entirely:

| | throws in 180 s | per request |
|---|---|---|
| dbitem | 888 762 | **1.031** |
| dblist | 559 790 | **1.046** |
| dbpost | 328 311 | **1.032** |

**Every request on this stack throws exactly one exception**, and it is `JobCancellationException` —
exceptions as control flow, which is what RQ7 suspects, at one per request. The throttled sample had
understated it by a factor of 6 to 17.

**And it costs nothing, for the reason §1.2 predicted before any of this ran.**
`JobCancellationException` overrides `fillInStackTrace` and is stackless, so a throw is an allocation
and no stack walk:

| | self samples in exception construction | every sample with an exception frame anywhere |
|---|---|---|
| dbitem | **0.154 %** | 0.73 % |
| dblist | **0.056 %** | 0.61 % |
| dbpost | **0.094 %** | 0.58 % |

The right-hand column overcounts badly on purpose — it charges the whole stack to the exception — and
even that is a third of the brief's 2 % line. **The exception half is green with room to spare.**

**Verdict: RQ7 is red on deoptimisation, green on exceptions, and the red is a criterion problem
rather than a service problem.** The honest sentence is that steady state on this stack is stable: a
handful of speculation failures a minute, one of which recurs at a site whose branch really is
unstable, and one stackless exception per request that costs a tenth of a per cent.

| Fact | Where verified |
|---|---|
| Deoptimisation events, buckets, steady-state rate and site intervals | `bench/profile/results/rq7-steady-state.md`, `bench/profile/rq7-steady-state.sh`, `rq7-analyse.py` |
| `jdk.JavaExceptionThrow` is throttled at 300/s; `jdk.ExceptionStatistics` is not | `openjdk-25.0.4!/lib/jfr/profile.jfc` |
| One exception per request, and its CPU share | `rq7-exception-total.py`, `rq7-exception-cost.py` over the committed pair profiles |

### 1.22 RQ4's deciding clause, tested at last: about a tenth of the gap, against a required third

RQ4's red has two halves and only the first was ever tested: above 1.5×, **and** at least a third of
the gap traceable to failed inlining, megamorphic dispatch or failed scalar replacement. §1.11
argued from a decomposition that the gap is work; an argument is not the test, and the brief's author
said so. This is the test.

Two arms behind one binary at the **same fixed 2000 rps**, so every frame they share cancels and what
is left is the layer. Both held the offered rate exactly.

| | µs CPU/req | p50 | allocation |
|---|---|---|---|
| `jdbc` | 762 | 2.26 ms | 57 388 B/req |
| `exposed` | 894 | 3.36 ms | 73 942 B/req |
| **the gap** | **132 µs (1.17×)** | | **+16 554 B/req** |

**The three named mechanisms, measured:**

| mechanism | how it is read | share of the 132 µs gap |
|---|---|---|
| megamorphic dispatch | `vtable stub` / `itable stub` frames: 2.19 % of the jdbc arm, 3.04 % of the exposed arm | **+10.5 µs — 8 %** |
| failed inlining leaving code uncompiled | interpreted frames: **0.00 % in both arms** | **0 %** |
| failed scalar replacement | bounded from above by the extra allocation — 16 554 B is 22 % of what the exposed arm allocates, and all GC is 1.2 % of its CPU | **≤ 2.4 µs — 1.8 %** |
| **together** | | **≈ 10 %** |

**The clause is not met, and not narrowly.** About a tenth of the gap traces to the three mechanisms
the brief names, against the third its red condition requires. The scalar-replacement figure
deliberately overstates — an object that escapes was never a candidate for scalar replacement, so
charging *all* the extra allocation to it is the safe direction for a threshold test, and it still
comes to under two per cent.

**Where the gap actually goes is data-structure work, and the largest piece is a surprise:**

| | µs/req |
|---|---|
| `ThreadLocal$ThreadLocalMap.getEntryAfterMiss` | **+13.7** |
| `HashMap.getNode` | +11.7 |
| `ArrayList.grow` | +10.0 |
| `itable stub` | +8.0 |
| `Intrinsics.areEqual` | +6.9 |
| `ResultRow$ResultRowCache.<init>` / `ResultRow$Companion.create` | +9.6 |

`HashMap.getNode` is the per-column `fieldIndex` lookup §1.11 already named. The one at the top is
new, and its stack says exactly what it is:

```
ResultRow.<init>
  → TransactionsKt.currentTransactionOrNull
    → ThreadLocalTransactionsStack.getTransactionOrNull
      → ThreadLocal.get → ThreadLocalMap.getEntryAfterMiss
```

**Exposed consults a `ThreadLocal` to find the current transaction every time it constructs a
`ResultRow`** — once per row, fifty times on this endpoint — and the lookup misses its direct hash
slot and falls into the linear probe. That is the single largest component of the Exposed-over-JDBC
gap, it is pure work, and it is on nobody's list of JIT questions.

**One caution the same run produced.** Frames owned by Exposed are 40.8 % of the exposed arm's CPU —
365 µs — against a gap of 132 µs. Owner-share is not cost: most of what Exposed's frames do is work
the JDBC arm also did, under different names. A table that read 40.8 % as "what Exposed costs" would
overstate it by nearly threefold.

**Verdict: RQ4 is not red, and now for a measured reason.** The red needs both halves; the second is
10 % against a required 33 %. The ratio itself remains rate-dependent — 1.17× here, 1.27–1.29× in
§1.11, 1.61× at saturation in §1.12 — so "green" as the brief words it is not a stable answer, but
"red" is now excluded outright. The gap is work, which is the brief's own category for a library
cost rather than a finding.

**The same warm-up caveat applies**: this run warmed for 45 s against the 90 s
[B-42](../backlog/B-42-warmup-gate-on-printcompilation.md) later measured, so both arms carry a
little unfinished compilation. They carry it equally — same binary, same rate, same warm-up — and the
measurement is a difference, which is the case where a shared bias cancels.

| Fact | Where verified |
|---|---|
| Both arms, their CPU per request, the three signals and the frame diff | `bench/profile/results/rq4-clause.md`, `bench/profile/rq4-clause.sh`, `rq4-diff.py` |

---

## 2. Where each research question stands

The brief's deliverable is one row per construct. This is that table as of 2026-09-19, after the
JMH set of §1.14–§1.18 and the review in §2.3: what is settled, what is priced, and what has not
been touched. A question can be
*settled* without being *priced* — knowing that a call site is megamorphic by construction is not
knowing what it costs — and the two are kept apart on purpose.

| RQ | State | What is known, and where |
|---|---|---|
| **RQ0** gate | **replaced** | D1, on grounds that were themselves corrected in review. Computed rather than asserted (B-53), the gate has **three answers** — 2.2–4.4 % red on all three endpoints under the narrowest attribution, 11.6–22.8 % green on all three under the broadest — because the brief never says where a `HashMap.get` sample reached from Exposed belongs. Its green and red also leave a hole: one endpoint over the bar is neither. A gate whose verdict is chosen by whoever runs it is not a gate |
| **RQ1** sizes | **GREEN** | Nothing on the path is within a factor of four of the huge-method limit (§1.8); of 638 methods over `FreqInlineSize` only **49 run**, owning 3.86 % of self samples together (§1.9); the dial provably fires — `hot method too big` refusals fall **155 → 3** — and moves CPU per request by **1.3 %, inside a 2.8–4.3 % ruler**, i.e. an effect bounded below ~4 % at n = 3 (§1.17) |
| **RQ2** megamorphic | **GREEN, measured** | Megamorphic by construction, and priced through the right dispatch table: **4.006 ns against 0.693 monomorphic, 5.8×** via vtable, not the 6.715/8.9× first reported through an interface (§1.19). Macro half now measured rather than argued: the brief's two sites together are **0.52–0.87 % of request CPU** as an upper bound, against its 2 % line (§1.20) |
| **RQ3** escape analysis | **GREEN on both halves** | Micro: nothing survives the non-suspending path. `-XX:-DoEscapeAnalysis` takes the arm from 16 to **168 B/op**, and the 16 B/op first reported as "the continuation" was the blackhole forcing the fast-path box to escape; consumed as an `Int` a suspend call is **0.911 ns against 0.917 plain, ≈0 B/op** (§1.15). Macro: a real request *does* suspend, and the machinery is **11–27 % of its 23–75 KB of allocation** — but all GC on this stand is 1.17–1.23 % of CPU, so that bucket is worth **0.13–0.33 %** (§1.20). Side finding: `Boxing.boxInt` is `new Integer`, never the cache |
| **RQ4** Exposed | **not red, measured** | The ratio is rate-dependent — **1.17×, 1.27–1.29×, 1.61×** at three operating points (§1.22, §1.11, §1.12) — and the brief's 1.5× line falls inside that range, so "green" as worded is not a stable answer. But red needs *both* halves, and the second is now tested: **≈10 % of the gap** traces to dispatch stubs (8 %), uncompiled code (0 %) and failed scalar replacement (≤1.8 %), against a required third (§1.22). The gap is work, and its largest single piece is a `ThreadLocal` miss per `ResultRow` |
| **RQ5** codegen patterns | **GREEN** | Measured on both halves (§1.18). Value classes through generics and nullables, `$default`, and capturing non-`inline` lambdas are **free — 0 B/op and inside 0.3 ns of their controls**. Two patterns the brief does not name are not: `Delegates.observable` at **15×** and one box per write, and an eager collection chain at **3.8×** and 7184 B/op. Both are now bounded by what a request allocates at all: **0.24 % and 0.18 % of request CPU** (§1.20). **2567 of 6700 classes on the path are Java** and cannot carry any of it |
| **RQ6** encoders | **GREEN** | A JSON-only service is monomorphic at these sites; sustained mixed traffic makes them **bimorphic at 50/50**, read out of the inlining log, and `TypeProfileWidth` is 2 — so C2 still profiles and inlines them (§1.16). A one-off tree call costs nothing measurable |
| **RQ7** steady state | **RED on deoptimisation, GREEN on exceptions** | Measured (§1.21). Steady state is **2.25–5.62 deoptimisations per minute**, above the brief's "under 1", and one site genuinely recurs — `CoroutineScheduler$Worker.tryPark()@40` at 31/26/16 s. Four events in 160 s: red by the letter of a threshold no healthy JVM under load appears to meet. Exceptions: **exactly 1.03 per request**, all `JobCancellationException`, costing **0.06–0.15 % of request CPU** because it is stackless as §1.2 predicted |

**Kill criterion 4 is met several times over.** The criterion is "three RQs in a row come out green
or grey". RQ1, RQ2, RQ3, RQ5 and RQ6 are green, RQ4 is not red on a clause now tested at 10 % against
a required third, and RQ7 is the phase's only red — on a threshold that, measured, appears to be one
no healthy JVM under load meets.

The brief's instruction is then to drop what remains and write that the stack is well served by C2.
On the evidence that is right, but it has to be said in the form the evidence supports, and an
earlier draft of this paragraph did not. It claimed **"every mechanism the brief suspected is real,
and none of them costs anything"** — and half of that was false in the direction that flatters the
study. Several of the mechanisms are not real: continuations do **not** survive escape analysis
(§1.15), and loading three encoder classes does **not** put three receivers on a call site (§1.16).
Two of them were artefacts of the measurement rather than properties of the stack.

The claim the evidence actually supports is narrower and is worth stating exactly:

> **Nothing in the brief's construct list has been shown to cost 2 % or more of request CPU, and
> most of it costs nothing measurable at all.** Where a mechanism exists, it is priced below the
> stand's resolution; where it was reported and then re-tested, it more often turned out not to exist
> than to be expensive.

The macro shares that sentence used to owe are now measured (§1.20): RQ2's two sites are 0.52–0.87 %
of request CPU, RQ3's allocation bucket 0.13–0.33 %, RQ5's two off-list patterns bounded at 0.24 %
and 0.18 %. RQ7 is measured too (§1.21). **One honest gap is left**: RQ4's deciding clause — above
1.5×, *and* a third of the gap from failed inlining, dispatch or scalar replacement — was never
tested. Everywhere else, "not shown to cost 2 %" is now a measurement saying so rather than an
argument.

What does cost — a transaction wrapper at 64 µs, a dispatcher default at 27 % on this box, a
co-located database taking a third of the machine — is on nobody's list of JIT questions.

### 2.1 What the brief did not ask, and the phase found anyway

These are not construct verdicts and do not belong in the table above. Each is measured with
repeats, and by the brief's own distinction each is library cost rather than a JIT failure — which
is exactly why they would have been lost had the study only filled in its own form.

| Finding | Size | Where |
|---|---|---|
| **JFR reports no compilation at all on the settings it ships with** — 7268 compile tasks, zero events — so a warm-up gate phrased against it cannot fail | qualitative, and fatal to the brief's protocol | §1.3 |
| `jdk.CompilerInlining` truncates after the first 8–96 compile ids of a recording, in eight recordings of eight | qualitative | §1.3 |
| **On this box, the default `Dispatchers.IO` size of 64 costs 27 % more CPU per request than 16** — measured at saturation, two rounds, and *not monotonic* in the parameter: 245 µs at 4, 166 at 16, 210 at 64, 235 at 128. §1.13 also argues that saturation numbers describe the stand, so this is a result about one four-core box with a co-located database, not a property of Ktor services | 210 µs against 166 | §1.13 |
| **Wrapping the same SQL in an explicit transaction costs ~64 µs per request**, flat in row count — as much as everything Exposed adds on a single-row read | 64 µs | §1.11 |
| **`kotlin.coroutines.jvm.internal.Boxing.boxInt` compiles to `new Integer(i)`, not `Integer.valueOf(i)`** — the coroutine fast path never consults the `Integer` cache, so an escaping suspend-returned primitive allocates on every call whatever its value | 16 B per escaping box | §1.15 |
| **`List.map`/`filter` are `inline` and leave no call site; `Sequence.map`/`filter` are not** — yet the lazy chain is 2.6× cheaper, because intermediate lists dominate dispatch. A census of call sites ranks the two backwards | 4592 ns vs 1750 | §1.18 |
| **Two fifths of the request path is Java** — Netty, the PostgreSQL driver and HikariCP are 2567 of 6705 classes and cannot carry a Kotlin codegen pattern at all | 38 % of classes | §1.18 |
| **Nine tenths of a static size shortlist is code that never runs** — 49 of 638, and the miss rate has to be computed over artifacts that could have appeared at all | 89 % | §1.9 |
| The real-mode ceiling on a four-core box with a co-located database is **the box**: 3.93 of 4 cores, of which the database takes 1.24 and the kernel 0.80 | — | §1.13 |
| **Every request on this stack throws exactly one exception** — 1.03 per request by the uncapped counter, all `JobCancellationException`. It costs 0.06–0.15 % of CPU only because it is stackless; the same pattern with a stack-filling exception would be a different finding | 1.03/req | §1.21 |
| **`jdk.JavaExceptionThrow` is throttled at 300/s in `profile.jfc`** and sat on that ceiling here, understating the throw count by 6–17×. Third time in this phase that a JFR default silently capped the thing being measured | 52 000 against 888 762 | §1.21 |
| **Starting and stopping a JFR recording deoptimises the service being recorded** — 59–62 events in the first ten seconds and 16–17 in the last, against single digits across the 160 s between | — | §1.21 |
| **C2's own threads cost 4.9–6.1 % of request CPU on a saturated four-core stand** — four to five times the collector, on a box running flat out where compilation should have settled. The same quantity Open question 3 found at 61 % in a one-core container | 5 % against GC's 1.2 % | §1.20 |
| **A request on this stack allocates 23–75 KB**, of which a quarter is coroutine machinery on the two small endpoints — and all garbage collection costs 1.2 % of CPU, so the size of the number and the size of its price are unrelated | 23 434 / 74 953 / 30 741 B | §1.20 |

### 2.2 Fourteen claims that were offered and withdrawn

Kept, all of them, because most looked convincing when they were written and none was visible in its
own numbers. Two patterns run through the list: a share measured inside one run survives while a
ratio between single runs does not, and a benchmark can find its subject and still measure something
else.

| Claim | Why it died |
|---|---|
| "JFR's compiler view is not a census" | The test program stopped compiling before the recording was live. On a workload that keeps compiling, coverage is 99.5 % (§1.3) |
| "The pool sets the real-mode ceiling" | Repeats: 16/32/64 give 5286/5069/4595 rps — more pool is monotonically *worse* (§1.12) |
| "Exposed sets the ceiling" | Hand-written JDBC hits the same wall at 1.67× the throughput (§1.12) |
| "The `Dispatchers.IO` size sets the ceiling" | It moves the price of a request from 245 to 166 µs and leaves cores at 1.77–2.02 (§1.13) |
| "Indexed iteration loses vectorisation" | Direct and indexed are within 3 %; the odd arm is the one that *multiplies* (§1.14). Now explained: `-XX:-UseSuperWord` takes the multiply arm from 71 to 401 ns/op and leaves the other three untouched, which is JDK-8345044 — SuperWord in JDK 25 refuses a reduction-only loop, and JDK-8340093 fixes it in JDK 26 ([B-52](../backlog/B-52-multiply-makes-the-loop-faster.md), closed) |
| "A suspend call costs 5.6× a plain one" | The plain side took a literal and constant-folded to 0.701 ns — about two cycles, which is the blackhole and nothing else (§1.15) |
| "16 B/op is the continuation" — *before it was shown* | The `suspend { }` literal sat inside the benchmark method and was allocated per call. Only the hoisted arm, created once and still allocating 16, made the claim safe (§1.15) |
| **"The continuation survives escape analysis; the boxed primitive is removed"** — the published RQ3 verdict | Exactly backwards. A continuation here is 32–40 bytes by field layout and cannot be 16; the 16 B/op was the blackhole forcing the fast-path box to escape, and `-XX:-DoEscapeAnalysis` shows the continuations at 168 B/op when EA is denied. Caught by the brief's author doing arithmetic on `javap -p` output (§1.15) |
| **"Moving the RQ3 input outside the `Integer` cache is the whole fix"** | `Boxing.boxInt` is `new Integer(i)` and never asks the cache, so the input value never mattered (§1.15) |
| **"RQ2 costs 6.715 ns against 0.755, 8.9×"** | Measured through an interface (itable). `resumeWith` → `invokeSuspend` is a virtual call on a class (vtable), which prices at 4.006 against 0.693, 5.8× (§1.19) |
| **"RQ4 is green: 1.27–1.29× against a 1.5× line"** | The ratio is rate-dependent and the two measurements straddle the line — 1.61× at saturation. The line was also specified for stub mode, and the deciding clause of the red condition was never tested (§1.11) |
| **"Stub mode reached 3.51 of 4 cores"** | The figure is in no results file, §1.10 does not contain it, and the fixed-rate pairs run the other way: stub takes fewer cores than real at the same rate (§1.13) |
| **"`startCoroutineUninterceptedOrReturn` has no JVM member"** | It has three, `private static final`, which is what `@InlineOnly` compiles to. The original `javap` ran without `-p` and public-only output was read as absence (§1.6) |
| "One `encodeToJsonElement` takes a site from one receiver to three" | Loading three classes is not putting three receivers on a site. Sustained mixing gives **two**, which the profile width covers (§1.16) |

One more belongs here without being a claim: the boxing arm of RQ3 summed 7 and 11, and 18 is inside
the `Integer` cache, so the arm meant to measure boxing measured nothing at all (§1.15).

---

### 2.3 The brief's author reviewed this document, and all eight objections held

The document was sent to the person who wrote the brief. They returned eight numbered objections. All
eight were checked here; **none was rejected**, one was found to understate the problem, and two
required new measurements to settle. They are listed in the order given.

| # | Objection | What checking it found |
|---|---|---|
| 1 | **16 B/op cannot be a continuation** — a state machine is ≥32 bytes by field layout, so RQ3's conclusion may be inverted and the 16 bytes may be the harness | **Right on both halves.** `javap -p` gives `Continuations$hoisted$1` six fields, 36 bytes padded to 40. `-XX:-DoEscapeAnalysis` moves the arm to 168 B/op, so the continuations *were* being scalar-replaced; the surviving 16 was the blackhole forcing the fast-path box to escape. RQ3 goes from grey to **green** (§1.15) |
| 2 | **RQ4's green depends on the denominator**, the 1.5× line was set for stub mode, and the study's own saturation number is 1.61× — the other side of the line | **Right**, and it opened the clause that decides the question. The ratio is 1.17–1.61× across three operating points, so the brief's line falls inside it. The untested second clause was then tested (§1.22): ≈10 % of the gap against a required third, so **RQ4 is not red** — measured, not argued from a decomposition |
| 3 | **No evidence the lever engaged** — `FreqInlineSize=2000` does not mean those methods were inlined, and "1.3 % inside a 2.8–4.3 % ruler" is an effect bounded below ~4 %, not zero | **Right to demand it, and the check passes.** Under `-XX:+PrintInlining`, `hot method too big` falls **155 → 3** — but `InlineSmallCode` refusals rise **324 → 453**, so a second gate does absorb part of it, exactly as suspected. The null is real; the wording was not (§1.17) |
| 4 | **RQ2's 580 is a classpath count, `resumeWith` runs only on real resumption, and itable ≠ vtable** | **Right on all three.** Measured through the dispatch table `invokeSuspend` actually uses: **4.006 ns against 0.693, 5.8×**, not 6.715/8.9×. And the resumption argument closed RQ2 by arithmetic — ~3900 calls needed against single-digit resumptions per request (§1.19) — which §1.20 then replaced with a measurement: the two sites are 0.52–0.87 % of request CPU |
| 5 | **B-52 is a known SuperWord heuristic**, JDK-8345044, not a stand fault — a reduction-only loop is refused vectorisation, which is why multiplying makes it faster | **Verified, and more exactly than expected.** JDK-8345044 is "Sum of array elements not vectorized", closed as a duplicate of JDK-8340093 "C2 SuperWord: implement cost model", which is **Fixed in JDK 26**, resolved 2025-11-10 — one release after the 25.0.4 this stand runs. The upstream reproducer is the same construct, and its numbers are the same shape: 552 → 142 ns/op there, 406.7 → 77.3 here. The stand reproduced a known bug it did not know about ([B-52](../backlog/B-52-multiply-makes-the-loop-faster.md)) |
| 6 | **Over-generalisation** — the `Dispatchers.IO` result is one box and non-monotonic, and "no mechanism costs anything" is only true as "not shown to reach 2–4 %" | **Right.** Both restated: the dispatcher finding now carries its non-monotonicity (245/166/210/235 µs) and its scope, and the summary sentence in §2 was replaced outright |
| 7 | **Stale and self-contradicting text** — §2 and §6 predate the JMH set, §2.1 lists a claim §2.2 retracts, 3.51 cores is attributed to a section that lacks it, 11 398 rps belongs to no configuration | **Right, and one item is worse than stale.** "3.51 of 4 cores" appears in **no results file at all**; the highest recorded is 3.87, from a five-second warm-up probe. The sentence has been withdrawn, not re-cited. The 11 398 figure exists but its arm was never recorded, so it is now marked as not comparable with §1.12 |
| 8 | **D1 argues with a different quantity than RQ0 defines**, and the gate should be computed rather than asserted | **Right.** RQ0 is CPU per request against p50 latency; D1 answers with CPU shares by owner. The division is one line over data already taken and is now [B-53](../backlog/B-53-compute-the-rq0-gate.md) rather than an assertion. Their own admission that the gate is rate-dependent — under 1 % at saturation — is recorded with it |

**The one point that this document had already reached independently** is that Open question 3
is the most interesting result in the phase: 61 % of self CPU in C2's own threads on a container-limited
service at 50 rps. Both of us rank it above every construct in the list, and it is the one thing the
brief's output shape has no row for.

**What this exchange says about the method.** Four of the eight — 1, 3, 4 and 7 — are cases where a
check found its subject, produced a plausible table, and was read wrongly; the numbers were right and
the sentence over them was not. Three of the thirteen retractions in §2.2 come from this single
review. The document's own discipline caught eight earlier errors of the same shape and did not catch
these, and the difference is that someone who had not run the benchmarks did arithmetic on their
premises instead of on their output.

## 3. Decisions

### D1. The gate is not RQ0 as written *(deviation from the brief)*

Brief: run RQ0 first, stop the study if JVM CPU is under 10 % of p50 latency on all three database
endpoints.

Decision: keep the measurement, drop its use as a gate, and gate on §1.1's numbers instead — the
share of the bucket that belongs to each owner. Why: the split inside RQ0's bucket is already
measured on this stack twice, and it is the split that decides which RQ can ever be red. The price:
the study loses the cheap early stop the brief wanted. It is replaced by a cheaper one — §1.1 costs
nothing to read.

**This decision used to be justified by the claim that RQ0's bucket "is nearly the whole process, so
it passes by construction".** The brief's author objected that this argues with a different quantity
than RQ0 defines — RQ0 is CPU per request over p50 latency, not CPU share by owner — and that the
gate should be computed rather than asserted. Both halves are right, and computing it (B-53, over the
runs already committed) turns out to matter, because **the gate does not have one answer.**

| attribution rule applied to the profile | dbitem | dblist | dbpost | the brief's verdict |
|---|---|---|---|---|
| **narrow** — self samples in `bench.`/`io.ktor.`/`kotlinx.`/`kotlin.` only | 2.5 % | 2.2 % | 4.4 % | **red on all three**, i.e. kill criterion 1 fires and the study never starts |
| **middle** — the above plus `java.*`/`jdk.*` and Netty, pgjdbc, Hikari | 7.5 % | 6.5 % | 12.0 % | **neither**: one endpoint over 10 %, which is not "two or more" and not "below 10 % on all three" |
| **broad** — every sample with a named owner on the stack; only native, kernel and JIT stubs excluded | 15.1 % | 11.6 % | 22.8 % | **green on all three** |

Taken from `bench-results/pair-real-db{item,list,post}` at 995/1181/1108 µs of CPU per request
against p50 of 6.22/9.56/4.55 ms.

**So the gate's answer is decided by a rule the brief does not state.** "CPU time in JVM code of the
application, Ktor, Exposed, serialisation and the JDBC driver" does not say whether a `HashMap.get`
sample reached from Exposed belongs to Exposed or to the JDK, and the three defensible readings of
that one sentence span red, undecidable and green. A second gap sits beside it: green is "at least
10 % on two or more" and red is "below 10 % on all three", so a run that clears the bar on exactly one
endpoint satisfies neither. And the author's own note adds a third — the gate is evaluated at no
stated offered rate, and the ratio moves with it.

**D1 therefore stands, on better grounds than it was first given.** The objection to RQ0 is not that
it passes by construction; it is that as written it cannot be evaluated without three decisions the
brief leaves to whoever runs it, and a gate whose verdict is chosen by the person it is meant to
constrain is not a gate. Had the narrow reading been taken, this study would have stopped at phase 2
and published "JIT behaviour is not a practical concern for this class of service" — which §1.18 and
§1.15 now show would have been the right conclusion for the wrong reason.

### D2. The macro unit is the share by owner and µs of CPU per request, never rps *(deviation)*

Brief: macro relevance is 2 % of request CPU, measured by samples or by the toggle's effect on CPU
per request.

Decision: shares by owner from async-profiler, plus `utime+stime` over the clean window divided by
responses, as the fourth phase established. rps is printed and never used for a verdict. Why:
§1.7 — rps moves ±13–15 % between identical runs on this host and the frequency cannot be fixed;
the ratio-shaped quantities survived that. The price: a share says where the CPU went, not what
removing it would save, so every red verdict still needs its toggle.

### D3. The warm-up gate reads `jdk.Compilation`; the inlining evidence cannot *(deviation)*

Brief: start measuring after JFR shows no C2 compilations for 60 seconds; JFR gives compiler
behaviour without diagnostic flags.

Decision, in two halves, because the two events behave differently (§1.3):

* **The gate uses `jdk.Compilation`, switched on explicitly** — `+jdk.Compilation#enabled=true` and
  `#threshold=0ms` — and the runs it gates carry that setting too, since at 99.5 % coverage it is a
  census, and at +5.3 % and +5.7 % across two sweeps of a microbenchmark that does nothing but
  compile it is affordable on a service that spends most of its time waiting for I/O. On the settings the brief assumes, the same gate cannot fail.
* **Inlining refusals come from `-XX:+PrintInlining` or `LogCompilation`, in their own runs.**
  `jdk.CompilerInlining` stops after a few dozen compilations, reproducibly. The brief is right that
  those flags change timing, so no timing number is taken from a run that carries them.

Why the correction is recorded rather than the conclusion alone: the first version of this decision
sent the gate to `-XX:+PrintCompilation` on the strength of a control that was too short to compile
anything after startup. The price of the decision as it now stands: two runs instead of one
wherever a verdict needs both an inlining reason and a time.

### D4. RQ1 keeps one dial and one bound

`-XX:FreqInlineSize` is the dial. `-XX:-DontCompileHugeMethods` is a bound with no subject: not one
method on the whole request path comes within a factor of four of 8000 bytes, and the three that
exceed it across 56 405 are cold (§1.8). Splitting a suspend function by hand stays, and §1.8 says
which one to split first — the four `invokeSuspend` bodies that run on every request belong to Ktor
and the Netty engine, not to the application.

The dial is now pointed at a list of 49 rather than 638 (§1.9), and the arm is the toggle rather
than the scan: the scan says which methods are large and which of them run, and neither of those is
the price of a refused inline.

### D5. Constructs are counted wherever they occur *(deviation from the brief)*

Brief: RQ5 lists Kotlin codegen patterns and prices each against 2 % of request CPU.

Decision: each pattern is counted across the whole process — kotlinx.serialization's generated
serialisers, Ktor's pipeline, Exposed's DSL and the application alike — and the report says which
owner each occurrence belongs to. Why: §1.1, consequence 2 — application code is 0.9–4.0 % of CPU,
so a pattern confined to it cannot clear 2 % and every RQ5 row would come out green by arithmetic.
The price: a red verdict then names a construct in somebody else's library, where the action is an
upstream ticket rather than a rewrite — which is what the brief's Non-goals already say happens to
findings.

### D6. The stand is `bench/` on Netty; no new repository

Follows the second phase's D3 and its reasoning. The four endpoints of this brief are added to the
existing service rather than replacing `/echo`, `/items`, `/business`, so that the numbers of
§1.1 stay comparable in the same tree. The data layer is added behind the brief's two modes, real
and stub. Netty is the default for this phase's runs and CIO stays available on `-Dbench.engine`,
because the engine turned out to move the application's share by a factor of two (§1.1).

### D7. Every version in the setup table is a version, not a range

Exposed is pinned at 1.4.0, Ktor at the version the stand already carries, and the JMH, JDBC
driver and HikariCP versions are written into the catalog before the first measurement. Why: the
brief's own rule, which its table does not keep — "Ktor 3.x" and an unpinned Exposed are not pins,
and §1.4's facts are true of 1.4.0 and of nothing else.

### D8. A pair with a known order runs beside the calibration controls

The brief's five controls are expected to be green and catch a harness that sees effects that are
not there. They do not catch a harness that has stopped measuring the subject. One extra pair is
added whose order follows from the code — a variant that does strictly everything another does and
one step more. If it comes out cheaper, the run is discarded and the stand is fixed, not the
number. Why: an impossible ordering is the fastest detector of a stand measuring itself, and it
fires before the spread does.

---

## 4. Risks and open questions

**Risk 1. The host cannot fix its own clock (§1.7).** Mitigation: µs of CPU per request as the
reported unit, variants interleaved, three repeats, and the within-variant spread printed beside
every between-variant difference. A difference smaller than the spread is not a result, and rows
whose spread exceeds ~1.3× are marked unusable in the output itself rather than in a footnote.

**Risk 2. The generator shares the subject's machine (§1.7).** It errs safely — the subject looks
healthier than it is — which is the dangerous direction for a study whose findings are all
"something costs less than you think". Mitigation: every row carries `gen=local`, and such rows are
not quoted as measurements of a limit; the dedicated pair used for sborka's brief C is the escape
when a number has to be defended.

**Risk 3. A green or grey verdict is the comfortable one and costs nothing to publish.** The brief
already has calibration controls for this. Mitigation on top: every green and grey verdict states
the control number that sizes it — what the smallest effect this chain demonstrably detects was, in
the same units — so that "no effect" and "not resolvable here" stay distinguishable in the write-up.

**Risk 4. The study re-derives §1.1 and arrives where the repository already stands.** Mitigation:
a candidate already priced by phase two starts from that number and the work is the toggle, not the
profile. The per-candidate budget of one working day is spent on causation.

**Open question 1 — closed 2026-09-19, and the hypothesis was wrong.** It asked why JFR's compiler
events covered only tens of milliseconds, and guessed at per-thread buffers that the compiler
threads stop flushing. The answer is that the question was about the test program: on a workload
that keeps compiling, `jdk.Compilation` covers 99.5 % of compile tasks (§1.3). What does truncate is
`jdk.CompilerInlining`, reproducibly and always at the start of the recording; why is not known, and
it is not worth knowing here, because the study needs that evidence at a fidelity JFR does not offer
anyway. If it is ever worth reporting upstream, the four repeats in
`experiments/jfr-compiler-events/results/` are the material.

**Open question 2 — closed 2026-09-19, and the answer is the opposite of the one it expected.** It
asked what a single `encodeToJsonElement` does to the shared encoder sites, on the reading that
three loaded classes are three receivers. B-46 measured it: under sustained mixing the sites settle
at **two** receivers, which `TypeProfileWidth=2` covers, and C2 keeps profiling and inlining them
(§1.16). The class census in §1.5 is right; the pollution it was read as predicting is not there.

**Open question 3. What C2 itself costs on a container-limited service.** At 50 rps under a
one-core limit, 61 % of self CPU was the JVM's own threads and the frames were C2's (§1.1). The
brief does not ask this, and on the class of service it is about — small containers, modest rates —
it may be the largest JIT-related number in the study. It is not a construct verdict, so it does
not fit the brief's output shape; it fits an article. Address: a dedicated run on konekt's own
stand, deciding nothing about the construct list.

---

## 5. Code anchors

| Kind | Code |
|---|---|
| stand | `bench/src/main/kotlin/bench/Main.kt` — the service and the engine switch |
| stand | `bench/profile/run.sh` — pinning, warm-up, clean window, CPU and alloc profiles |
| stand | `bench/profile/ab.sh`, `bench/profile/engines-ab.sh` — interleaved variants, medians |
| stand | `bench/profile/attribute.py` — self/owner attribution of collapsed stacks |
| experiment | `experiments/jfr-compiler-events/run.sh` — §1.3, the short workload |
| experiment | `experiments/jfr-compiler-events/long-run.sh` — §1.3, the control that corrected it, and the price of each instrument |
| experiment | `experiments/json-encoder-census/run.sh`, `experiments/json-encoder-census/Probe.kt` — §1.5 |
| experiment | `experiments/method-sizes/run.sh`, `experiments/method-sizes/scan.py` — §1.8 |
| experiment | `experiments/method-sizes/intersect.py` — §1.9, the join with the profile |
| stand | `bench/profile/jit-pair.sh` — §1.10, the two-host protocol with a shared rate per endpoint |
| stand | `bench/src/main/kotlin/bench/Data.kt` — §1.10, the two data modes |
| measurement | `bench/profile/results/pair-stand-notes.md` — §1.10, the stand's ruler and every probe behind it |
| measurement | `bench/profile/results/pair-rq4-arms.md` — §1.11, the three arms with every round |
| measurement | `bench/profile/results/pair-ceiling.md` — §1.12, the ceiling with repeats |
| measurement | `bench/profile/results/pair-ceiling-mechanism.md` — §1.13, who owns the machine |
| microbenchmark | `microbench/src/jmh/kotlin/micro/` — §1.14, controls, RQ2, RQ3 |
| microbenchmark | `microbench/results-controls.md`, `microbench/results-candidates.md` — §1.14 |
| microbenchmark | `microbench/results-rq3.md` — §1.15, allocation on the non-suspending path |
| microbenchmark | `microbench/results-rq6.md` — §1.16, the three arms and the type profiles |
| measurement | `bench/profile/results/pair-rq1-lever.md` — §1.17, the FreqInlineSize toggle |
| profile | `bench/profile/results/netty-jit/` — §1.9, the run the join reads |
| JDK configuration | `openjdk-25.0.2!/lib/jfr/profile.jfc`, `openjdk-25.0.2!/lib/jfr/default.jfc` — §1.3 |
| artefact | `org.jetbrains.exposed:exposed-core:1.4.0!/org/jetbrains/exposed/v1/core/ResultRow.class` |
| artefact | `org.jetbrains.exposed:exposed-core:1.4.0!/org/jetbrains/exposed/v1/core/IColumnType.class` |
| artefact | `org.jetbrains.kotlin:kotlin-stdlib:2.4.10!/kotlin/coroutines/jvm/internal/BaseContinuationImpl.class` |
| artefact | `org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.11.0!/kotlinx/coroutines/JobCancellationException.class` |
| artefact | `org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.11.0!/kotlinx/serialization/json/internal/StreamingJsonEncoder.class` |

---

## 6. What happens next

The order of work and its acceptance criteria are in the backlog, stage `stage-8-jit-constructs`.

**Done.** The stand has a database and the brief's four request shapes, in two modes behind one
binary ([B-41](../backlog/B-41-jit-stand-data-layer-and-endpoints.md)); the classpath-wide size scan
and its join with a profile ([B-43](../backlog/B-43-static-scan-across-owners.md), §1.8–1.9); RQ4 in
three arms ([B-45](../backlog/B-45-rq4-exposed-read-path-in-three-arms.md), §1.11); the real-mode
ceiling, answered ([B-51](../backlog/B-51-real-mode-ceiling-and-its-ruler.md), §1.12–1.13); JMH with
its calibration controls passing ([B-44](../backlog/B-44-calibration-controls-and-the-known-order-pair.md),
§1.14); RQ2 and RQ3 priced ([B-47](../backlog/B-47-rq2-rq3-continuation-machinery.md), §1.15, §1.19); RQ6's
receiver census ([B-46](../backlog/B-46-rq6-encoder-receiver-census.md), §1.16); RQ1's lever, pulled
and shown to engage (§1.17); and RQ5 on both halves — prices and counts across owners
([B-48](../backlog/B-48-rq1-rq5-sizes-and-codegen-patterns.md), §1.18).

**Every research question now has a verdict, and every verdict has a measurement under it.** §2.3
records that the brief's author reviewed the first version and that all eight objections held; the
fork this section used to describe — stop and write up, or bring JMH in — is long closed. JMH came
in, and between it and the profiles that followed, three verdicts changed (RQ3 grey to green, RQ4
green to amber to not-red, RQ7 from untouched to the phase's only red) and one number was corrected
(RQ2's, through the right dispatch table).

**What is left is not a research question.** Two things:

1. **C2's own CPU under a container limit.** §1.20 measured the compiler's threads at **4.9–6.1 % of
   request CPU** on a saturated four-core box — four to five times the collector — and Open question 3
   found **61 %** in a one-core container at 50 rps. Neither end is a construct verdict, so the brief's
   output shape has no row for it; it is the article's strongest material and the largest JIT-related
   number the phase produced.
2. **The write-up itself** ([B-50](../backlog/B-50-verdict-table-and-write-up.md)) — §2 is the verdict
   table the brief asks for, §2.1 the findings its form did not ask for, and §2.3 the review.

**Done since this list was last written:** RQ4's deciding clause (§1.22), which closes the last
research question — about a tenth of the gap traces to the three mechanisms the brief names, against
a required third, and the largest single piece of it is a `ThreadLocal` miss per `ResultRow`; RQ7
(§1.21), which was the only question never measured —
red on a deoptimisation threshold that appears unreachable, green on exceptions by a wide margin; the
three macro shares (§1.20); the RQ0 gate
([B-53](../backlog/B-53-compute-the-rq0-gate.md)), which turned out to have three answers; and
[B-52](../backlog/B-52-multiply-makes-the-loop-faster.md), closed — `-XX:-UseSuperWord` takes the
multiply arm from 71 to 401 ns/op and leaves the other three untouched, so the advantage is
vectorisation and the plain arms never had it. The stand reproduced JDK-8345044 without knowing it.

**Open question 3 is still the largest number in the phase and still has no row in the brief**, and
§1.20 now gives it a second data point: 61 % of self CPU in C2's threads at 50 rps under a one-core
limit (§1.1), and 4.9–6.1 % on a saturated four-core box. Both this document and the brief's author
rank it above every construct in the list. It is not a construct verdict, so it fits an article
rather than the table.

**What no further work can fix on this stand**: the governor cannot be fixed on any available host
(§1.7), and the real-mode ceiling is the four-core box (§1.13). Both are stated as properties of the
stand, and every absolute in real mode carries them.

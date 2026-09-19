---
id: B-41
title: "The stand grows a database: Exposed over Postgres in two modes, and the brief's four endpoints"
status: done
priority: P1
size: M
stage: stage-8-jit-constructs
---

# B-41 — A stand that has a data layer, and can take it away again

> **Done 2026-09-19.** Both modes behind one binary, verified byte-identical in their responses, on
> the dedicated pair with the generator off-host. Every measurement from §1.10 onward runs on it.
> Two things it taught that were not in the plan: the subject cannot build the project at all (no
> public IPv4, so the settings plugin will not resolve — the distribution is built elsewhere and
> shipped, which also makes both hosts run one artefact), and the four-core box cannot hold a
> database and a service at once ([B-51](B-51-real-mode-ceiling-and-its-ruler.md)).

The stand of the second phase has no database: `/items` is an in-memory store and `/business` is a
quote calculation. The fifth phase's brief is about a request path that goes through Exposed and a
JDBC driver, and about whether any JIT effect matters *next to I/O* — neither question can be
asked on a service that never waits for anything.

- **The four endpoints of the brief are added, the three existing ones stay.** `/plaintext`,
  `/items/{id}`, `/items?limit=50` and `POST /items` join `/echo`, `/items`, `/business` rather
  than replacing them, so the shares in
  [research-jit-constructs](../research/research-jit-constructs.md) §1.1 remain comparable in the
  same tree. The existing `/items` keeps its path and its in-memory store; the new ones live under
  their own prefix.
- **Two data modes, one binary.** Real — Postgres on the host, Exposed DSL over JDBC with
  HikariCP. Stub — the repository returns pre-built rows. Selected the way the engine already is,
  by a system property, so that a variant differs by one line of the start command.
- **Netty is the default engine for this phase** (D6), CIO stays on `-Dbench.engine`. The engine
  moves the application's share of CPU by a factor of two, so it is a pinned choice and not a
  default inherited by accident.
- Versions are pinned before the first measurement (D7): Exposed 1.4.0, the JDBC driver, HikariCP
  and JMH written into the catalog, not left as ranges.
- Does **not** cover: the DAO layer, R2DBC, or MongoDB — all three are the brief's non-goals.

- AC: `-Dbench.data=stub` and `-Dbench.data=real` both serve all four endpoints; `run.sh` reports a
  `cost:` line per endpoint in both modes; the real mode's schema and seed are created by the same
  script that starts the run, so a fresh machine reproduces it.
- AC: the stand resets what it accumulates before **each** run, not between groups — rows inserted
  by `POST /items` are state, and a stand that keeps them drifts monotonically.
- Anchors: `bench/build.gradle.kts`, `bench/src/main/kotlin/bench/Main.kt`, `bench/profile/run.sh`.

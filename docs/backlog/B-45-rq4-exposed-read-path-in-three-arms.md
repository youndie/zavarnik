---
id: B-45
title: "RQ4 answered green; what is left is the microbenchmark arm the service cannot stage"
status: done
priority: P1
size: M
stage: stage-8-jit-constructs
---

# B-45 — Exposed costs 1.3× hand-written JDBC, against a line drawn at 1.5×

> **Done 2026-09-19.** Three arms on the pair, two endpoints, three interleaved rounds at a fixed
> rate: `exposed / jdbc` is **1.27×** on a single row and **1.29×** on fifty, so RQ4 is **green** by
> the brief's own threshold. Spreads 2–7 %, against the 30 % the same stand showed on single
> windows the day before. Numbers and decomposition:
> [research-jit-constructs](../research/research-jit-constructs.md) §1.11.

What the decomposition says, and it is the useful half:

- **the transaction wrapper is ~64 µs per request and flat** — as much as everything Exposed adds
  on a single-row read, and not a JIT question at all;
- **Exposed's fixed part is ~70 µs**, its mapping **0.76 µs per row**, about **0.151 µs per column**;
- so the per-column `HashMap` lookup and `valueFromDB` dispatch that §1.4 suspected are real and
  amount to 5 % of a fifty-row request. The mechanism was right and the size makes it a footnote.

What is left, and it is deliberately not carried by this item:

- **The typed-row-holder arm needs JMH.** It cannot be staged at service level, because Exposed
  exposes no index-based row access. The per-row term bounds what it could show: 0.76 µs a row is
  the whole of what a perfect holder could win, so the arm is worth running only if something else
  brings JMH into the phase.
- **The 1.3× carries the stand's caveats**: one pool size, one rate, one machine pair whose governor
  cannot be fixed, and a real-mode ceiling that is still unexplained ([B-51](B-51-real-mode-ceiling-and-its-ruler.md)).

- Anchors: `bench/src/main/kotlin/bench/Data.kt`, `bench/profile/results/pair-rq4-arms.md`.

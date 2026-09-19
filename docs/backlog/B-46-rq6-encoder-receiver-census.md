---
id: B-46
title: "RQ6: price the step from one encoder to three, which one line of application code buys"
status: open
priority: P2
size: S
stage: stage-8-jit-constructs
blocked_by: [B-44]
---

# B-46 — The census is done; what is left is what it costs

Counted rather than guessed
([research-jit-constructs](../research/research-jit-constructs.md) §1.5): a process that only
encodes and decodes JSON bodies loads **one** concrete encoder, `StreamingJsonEncoder`. One
`encodeToJsonElement` anywhere in the same process loads **three**, and `TypeProfileWidth` is 2 —
so that one line takes every shared `Encoder` call site from the best case C2 has to megamorphic.
Ktor's own converter never touches the tree API, so the polluting call is always application code.

- **The two arms are the item.** The same endpoint, the same load, with and without a single
  `encodeToJsonElement` on a path that is not the endpoint's — a health handler will do, which is
  also the most realistic way it happens.
- **The evidence is the receiver count at the site**, read from `-XX:+PrintInlining` under load, not
  the class-loading census that is already committed. The census says the receivers exist; only the
  log says how many the site saw and whether it inlined.
- **Polymorphic serialisation of a sealed hierarchy is reported separately**, as the brief asks: a
  different receiver set and a different answer.
- **If the gap is real, it is worth more than a verdict row.** "One call in an unrelated handler
  deoptimises your JSON path" is a finding about kotlinx.serialization's users, and the brief's
  non-goals already say findings become upstream tickets.
- Does **not** cover: other formats, or the one-vs-two-active-formats arm the brief proposes — with
  a single format pinned (§Fixed setup) that arm has no subject here.

- AC: receiver counts and inlining outcomes at the generated serialiser's `Encoder` call sites, in
  both arms, from the compilation log.
- AC: the difference in µs of CPU per request between the arms, with the within-variant spread
  beside it, and the B-44 control number if it comes out green.
- Anchors: `experiments/json-encoder-census/run.sh`, `bench/src/main/kotlin/bench/`,
  `bench/profile/run.sh`.

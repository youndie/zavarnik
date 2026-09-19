---
id: B-46
title: "RQ6: price the step from one encoder to three, which one line of application code buys"
status: done
priority: P2
size: S
stage: stage-8-jit-constructs
---

# B-46 — Priced, green, and the census's consequence was wrong

> **Done 2026-09-19.** RQ6 is **green**, and stays green under sustained mixing.
> [research-jit-constructs](../research/research-jit-constructs.md) §1.16.
>
> * a one-off `encodeToJsonElement` costs **nothing measurable** — 25.386 ± 1.035 µs against
>   25.427 ± 0.972, allocation identical to three decimals, with the class loading verified at 2
>   classes against 7 so the manipulation is not assumed;
> * sustained mixing does move the profile, and the inlining log shows how far: the shared sites go
>   from **100 % `StreamingJsonEncoder`** to **50/50 with `JsonTreeEncoder`**. That is *bimorphic*,
>   and `TypeProfileWidth` is 2, so C2 still profiles and inlines behind a two-way guard;
> * the throughput arm for sustained mixing is **unusable and recorded as such**: it allocates 3.4×
>   what its control does, because the tree path builds a `JsonElement`, and no control made of
>   `encodeToString` can subtract that.
>
> **The census's consequence in §1.5 is corrected.** Loading three encoder classes does not put
> three receivers on a site. One line of application code buys bimorphism, which C2 handles — not
> megamorphism, which it does not.

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

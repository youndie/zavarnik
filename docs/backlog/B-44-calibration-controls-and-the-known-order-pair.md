---
id: B-44
title: "Five calibration controls plus a pair whose order the code already decides"
status: done
priority: P1
size: M
stage: stage-8-jit-constructs
---

# B-44 — Before any candidate, prove the chain can see and prove it is measuring the subject

> **Done 2026-09-19.** All five of the brief's controls are green and the sixth pair — the one whose
> order the code fixes — came out in the right order: 179.7 ns for the base and 269.5 for the same
> loop plus one multiply. [research-jit-constructs](../research/research-jit-constructs.md) §1.14,
> raw output in `microbench/results-controls.md`. Kill criterion 2 is satisfied, so every verdict
> taken with this chain counts.
>
> **The smallest effect the chain demonstrably resolves**, which every later green has to quote: the
> tightest control pair is the data-class `copy` at 5.354 ± 0.119 against 5.593 ± 0.151 ns — so
> differences below roughly **0.3 ns, or 5 % at that scale**, are not distinguishable here.
>
> The controls also turned up an anomaly nobody was looking for, now [B-52](B-52-multiply-makes-the-loop-faster.md).

The brief's five controls — `inline` functions with lambdas, `Intrinsics` parameter checks, `when`
over a sealed hierarchy, loops over ranges and arrays, data class accessors and `copy` — are
expected to be green and exist to catch a harness that reports effects that are not there.

They do not catch the other failure: a harness that has stopped measuring the subject at all. The
second phase met exactly that — an inlining log with 4744 decisions and zero mentions of the code
it was about — and read the zero as a result until the instrument was checked
([research-optimizer](../research/research-optimizer.md) §1.4).

- **A sixth arm is added whose order is known from the code** (D8): a variant that does strictly
  everything another does and one step more. If it measures cheaper, the run is discarded and the
  stand is repaired — not the number. An impossible ordering fires before the spread does.
- **Each control's number is kept, not just its verdict.** The smallest effect the chain
  demonstrably detected is the size of every later green: a construct reported as "no effect" means
  "below *this*", and the write-up has to be able to say what this is (Risk 3).
- **If a control comes out red, work stops** — the brief's kill criterion 2, kept as written, with
  its two-day budget.
- Does **not** cover: any RQ. Nothing downstream starts until this item closes.

- AC: six committed control results, each with its ns/op or B/op, its spread across repeats, and
  the toggle that moved it; the five expected greens are green.
- AC: one sentence, committed, naming the smallest effect this chain detected and in what units —
  quoted by every later green and grey verdict.
- Anchors: `bench/profile/ab.sh`, `bench/profile/attribute.py`.

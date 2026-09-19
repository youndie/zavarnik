---
id: B-44
title: "Five calibration controls plus a pair whose order the code already decides"
status: open
priority: P1
size: M
stage: stage-8-jit-constructs
blocked_by: [B-41, B-42]
---

# B-44 — Before any candidate, prove the chain can see and prove it is measuring the subject

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

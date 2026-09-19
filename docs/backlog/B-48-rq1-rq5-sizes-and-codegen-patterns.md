---
id: B-48
title: "RQ1 and RQ5: the size threshold with the one dial that exists, and the codegen patterns counted across owners"
status: wip
priority: P2
size: M
stage: stage-8-jit-constructs
---

# B-48 — RQ1 answered green; RQ5 still needs its per-pattern counts

> **RQ1 done 2026-09-19, RQ5 open.** Raising `FreqInlineSize` from 325 to 2000 — past every method
> that runs on this path — moves CPU per request by **1.3 %, inside a ruler of 2.8–4.3 %**. With
> §1.8's huge-method half already green and §1.9's 49 running methods owning 3.86 % of self samples
> between them, RQ1 is green on both halves:
> [research-jit-constructs](../research/research-jit-constructs.md) §1.17.
>
> The chain found its subject at every step and the toggle still returned nothing. That is the
> difference between a mechanism and a cost, and it is what the brief asks each row to separate.
>
> **What remains is RQ5**: the per-pattern counts across owners, per D5. It is the one question in
> the phase that has neither been measured nor bounded by something already measured.

Two of the brief's questions share a subject: a method C2 refuses to inline, and the constructs
that make methods that size. The second phase already found the refusals on this stand — five under
load, all "hot method too big", the largest application method 1827 bytes and none above 8000
([research-jit-constructs](../research/research-jit-constructs.md) §1.1).

- **RQ1 has one dial and one bound** (D4). `-XX:FreqInlineSize` is the dial.
  `-XX:-DontCompileHugeMethods` is a bound with no subject on this stand, since nothing reaches
  8000 bytes; `HugeMethodLimit` is a `develop` flag and cannot be set on a product VM at all.
  Splitting a suspend function by hand is the arm that tests the brief's actual suspicion — that
  `transaction {}` inflates `invokeSuspend` past the threshold.
- **RQ5's patterns are counted wherever they occur** (D5): value classes through generics, nullable
  types and interfaces; capturing non-inline lambdas; `$default` methods; collection chains against
  `Sequence`; delegated properties — in kotlinx, Ktor and Exposed as much as in the application,
  with the owner named on every row. Confined to application code, every one of them is green by
  arithmetic before it is measured.
- **A red row in a library becomes an upstream ticket**, not a rewrite — the brief's own non-goal.
- Does **not** cover: fixes. Nothing here patches a dependency.

- AC: per refusing method, the effect of raising `FreqInlineSize` on ns/op and on CPU per request,
  with spreads; and the effect of the hand split on the `invokeSuspend` the log names.
- AC: per RQ5 pattern, a row with occurrence count by owner, the micro effect, the macro share, and
  the verdict — green, red or grey — with the B-44 control number beside every green.
- Anchors: `bench/src/main/kotlin/bench/Pricing.kt`, `bench/profile/run.sh`.

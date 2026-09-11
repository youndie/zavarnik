# crac-cpu

Does a CRaC snapshot travel between two different CPUs, what `-XX:CPUFeatures` does about it, and
what that flag costs. This is `B-35`; the pair of machines is the one `B-09` has been waiting for.

Two hosts, one **pinned image digest** so the JDK is byte-identical on both — a snapshot verifies
the build id of every file it had mapped, so anything less would confuse a CPU verdict with a file
one:

| | CPU | features |
|---|---|---|
| the wide one | AMD EPYC-Genoa (KVM guest, 2 cores) | `avx2`, `avx512f` and the rest of AVX-512 |
| the narrow one | Intel Core Ultra 7 255HX (WSL2, 20 cores) | `avx2`, `avx_vnni`, no AVX-512 |

- `cross.sh checkpoint <dir> [flag]` / `cross.sh restore <dir>` — the two halves, run on the two
  hosts with `<dir>` carried between them; `Probe.java` is the one-class program inside.
- `cost.sh [runs]` — what the flag costs, measured where it can be: **one** machine, one image, two
  snapshots of the Ktor sample differing only by `-XX:CPUFeatures=generic`.

## Does it travel? (`results/2026-09-11-cross-genoa-coreultra.log`)

| checkpoint on | flag | restore on | result |
|---|---|---|---|
| wide | none | narrow | **refused**, exit 1, and the message names the mask to use |
| wide | `generic` | narrow | **SIGSEGV**, exit 139, no message at all |
| narrow | `generic` | wide | **restored** |
| narrow | none | wide | **refused**, exit 1, same message |
| wide | `generic` | wide (itself) | restored — the control that says the flag did not break the snapshot |
| wide | `generic`, **CRIU engine** | narrow | **SIGSEGV**, exit 139, no message — `results/2026-09-11-cross-criuengine.log` |
| wide | `generic`, CRIU engine | wide (itself) | restored — the same control for the other engine |

Two things follow, and the second is the one worth remembering.

**Without the flag the constraint is an exact match, not a subset.** Both directions refuse,
including narrow → wide, where every instruction the snapshot could want is present. The engine
says so in as many words — `Image constraint 'cpu.features' (bitmap subset) does not match` — and
prints both bitmaps.

**`generic` makes a snapshot portable upwards only.** Taken on the narrow CPU it restores on the
wide one; taken on the wide CPU it does not restore on the narrow one — it *crashes*, silently,
with no JVM message and no `hs_err` file. So following the JVM's own advice (`try using
-XX:CPUFeatures=0x… on checkpoint`, which is what it prints on the refusal) replaces a loud refusal
with a segmentation fault. The flag is also `not restore-settable`, which the JVM says plainly if
you try.

**It is not the engine.** The same `generic` snapshot taken with `-XX:CRaCEngine=criuengine` — the
older CRIU-based engine, which needs `CHECKPOINT_RESTORE` and `SYS_PTRACE` — restores on the
machine that took it and crashes the same silent way on the other. Two engines, one crash: the
fault is on the JVM's side of the flag, which is where a report about it belongs.

**The practical rule:** take the snapshot on the narrowest CPU it will ever be restored on, with
`-XX:CPUFeatures=generic`. Anything else either refuses or crashes.

## What `generic` costs (`results/2026-09-11-cost-of-generic-coreultra.log`)

The Ktor sample on the narrow machine, six restores of each snapshot in alternation, `oha` for ten
seconds at 32 connections against `/api/warm`:

| | no flag | `-XX:CPUFeatures=generic` |
|---|---|---|
| ready, median of 6 | 42 ms (42–60) | 46 ms (43–56) |
| requests/s, median of 6 | 5 526 (4 377–5 711) | 4 089 (2 499–4 956) |

Readiness does not change; throughput does, and it is lower in **all six** pairs. The magnitude is
not trustworthy — the ranges overlap badly and the box was not isolated — but the direction is
consistent, and it is what a baseline instruction set should do to code the snapshot compiled
before it was taken.

## Two ways to fool yourself, both met here

- **An empty snapshot directory also answers "incompatible or missing CPU features".** A transfer
  that dropped the image reads exactly like a CPU verdict. `cross.sh restore` therefore checks the
  image is there before it concludes anything.
- **`jcmd` cannot attach to a JVM started under an unmapped uid**, which is what
  `docker run --user "$(id -u)"` gives you. It answers `Could not find any processes matching`, the
  checkpoint never happens, and the empty directory that results leads straight into the trap
  above. The checkpoint here runs as root in the container and hands the files back afterwards.

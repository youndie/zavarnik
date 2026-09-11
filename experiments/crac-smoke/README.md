# crac-smoke

Can this box checkpoint and restore a JVM at all, and what survives it? Zulu 25.0.4.1 with CRaC
(`azul/zulu-openjdk:25-jdk-crac`), Docker 29 on the Linux box (WSL2 kernel 6.6).

- `run.sh` — `Hello.java` prints a tick a second with `System.nanoTime()`, wall time,
  `java.util.Random`, `ThreadLocalRandom` and `UUID.randomUUID()`; checkpointed with
  `jcmd Hello JDK.checkpoint`, restored in a fresh container. Tried in order: no extra privileges,
  `--cap-add CHECKPOINT_RESTORE --cap-add SYS_PTRACE`, `--privileged`.
- `restore-twice.sh` — one checkpoint, two restores of the same image, side by side; also prints
  every `CRaC*` flag of the JVM with its default (`-XX:+PrintFlagsFinal`).
- `randoms.sh` with `Randoms.java` — which generators repeat, by what holds their state and when
  it was made: `Random`, `ThreadLocalRandom`, `SplittableRandom` each from before and after the
  checkpoint, `Math.random()`, `RandomGenerator.getDefault()`, `UUID.randomUUID()`, and
  `SecureRandom` both unseeded and seeded.
- `generator-guard.sh` — the check D4 of the research asks for, as a prototype: restore one
  snapshot twice and fail with the **names** of the generators that gave both replicas the same
  number. Not a comparison of the application's answers, which a server's own consumption of the
  stream makes green for the wrong reason.

Results of 2026-09-11 (`results/`): the default engine on this build is **warp**, and checkpoint
and restore succeed **with no extra privileges** — the two other attempts add nothing. The
hello-world image is 33 MB. `nanoTime` continues across the pause (the tick after restore reports
the wall time that passed). Two restores of one image print **the same** `java.util.Random` and
`ThreadLocalRandom` sequences and **different** UUIDs: `SecureRandom()` is reseeded by the JDK
after restore, the other two are not.

`randoms.sh` says which of them repeat, and the third row is the surprise: a thread created
*after* the restore draws the same first `ThreadLocalRandom` value in every replica, because the
seeder that initialises new threads is in the snapshot too.

| Generator | Same across two restores? |
|---|---|
| `Random` constructed before the checkpoint | **yes** |
| `ThreadLocalRandom` on a thread that predates the checkpoint | **yes** |
| `ThreadLocalRandom` on a thread created after the restore | **yes** |
| `SplittableRandom` constructed before the checkpoint | **yes** |
| `SplittableRandom` constructed after the restore | **yes** — the same seeder mechanism |
| `Math.random()` | **yes**, once anything has called it before the checkpoint |
| `new Random()` constructed after the restore | no — `System.nanoTime` is in its seed |
| `RandomGenerator.getDefault()` | no |
| `UUID.randomUUID()` | no — `SecureRandom` underneath |
| `SecureRandom()` | no — the JDK reseeds it |
| `SecureRandom(byte[] seed)` | no — documented as *not* reseeded, yet the default Linux provider mixes the seed with system entropy, so the output differs anyway |

`2026-09-11-generator-map.log` is the guard's own run naming six of them;
`2026-09-11-generator-users-by-jar.log` says which library jars reference which generator, by
their constant pools: HikariCP and `exposed-jdbc` stand on `ThreadLocalRandom`, `flyway-core`
and `kotlin-reflect` on `Random`, `postgresql` on `SecureRandom`, and every Kotlin caller of
`Random.Default` on `ThreadLocalRandom` through the stdlib.

What that does *not* mean is that a service hands out repeated identifiers on demand:
`2026-09-11-konekt-esim-two-restores.log` has five restores of one konekt snapshot issuing five
different eSIM activation codes from `kotlin.random.Random.Default`. The sequences are the same;
what differs is how much of them the sign-in, the token, the screens, Exposed and Hikari consumed
first, on whichever pool thread served the request.

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
  it was made: a `Random` from before the checkpoint, `ThreadLocalRandom` on an old thread and on
  one created after the restore, a `Random` made after the restore, and `SecureRandom`.

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
| `new Random()` constructed after the restore | no — `System.nanoTime` is in its seed |
| `SecureRandom()` | no — the JDK reseeds it |

What that does *not* mean is that a service hands out repeated identifiers on demand:
`2026-09-11-konekt-esim-two-restores.log` has five restores of one konekt snapshot issuing five
different eSIM activation codes from `kotlin.random.Random.Default`. The sequences are the same;
what differs is how much of them the sign-in, the token, the screens, Exposed and Hikari consumed
first, on whichever pool thread served the request.

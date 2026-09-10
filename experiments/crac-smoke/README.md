# crac-smoke

Can this box checkpoint and restore a JVM at all, and what survives it? Zulu 25.0.4.1 with CRaC
(`azul/zulu-openjdk:25-jdk-crac`), Docker 29 on the Linux box (WSL2 kernel 6.6).

- `run.sh` — `Hello.java` prints a tick a second with `System.nanoTime()`, wall time,
  `java.util.Random`, `ThreadLocalRandom` and `UUID.randomUUID()`; checkpointed with
  `jcmd Hello JDK.checkpoint`, restored in a fresh container. Tried in order: no extra privileges,
  `--cap-add CHECKPOINT_RESTORE --cap-add SYS_PTRACE`, `--privileged`.
- `restore-twice.sh` — one checkpoint, two restores of the same image, side by side; also prints
  every `CRaC*` flag of the JVM with its default (`-XX:+PrintFlagsFinal`).

Results of 2026-09-11 (`results/`): the default engine on this build is **warp**, and checkpoint
and restore succeed **with no extra privileges** — the two other attempts add nothing. The
hello-world image is 33 MB. `nanoTime` continues across the pause (the tick after restore reports
the wall time that passed). Two restores of one image print **the same** `java.util.Random` and
`ThreadLocalRandom` sequences and **different** UUIDs: `SecureRandom()` is reseeded by the JDK
after restore, the other two are not.

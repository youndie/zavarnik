# crac-ktor

The Ktor CIO sample (`samples/ktor`, installed) under Zulu 25.0.4.1 with CRaC, in Docker on the
Linux box: what a checkpoint says while the server is up, and what a restore is worth.

- `run.sh N` — N plain starts timed from `docker run` to the first 200 on `/health`; then a
  checkpoint with the server up (`jcmd sample.MainKt JDK.checkpoint`) after 200 warm requests;
  if an image comes out, N restores timed the same way and one functional request after restore.
  `POLICY=policies.yaml` mounts a `jdk.crac.resource-policies` file; `SKIP_PLAIN=1` skips the
  baseline.
- `policies.yaml` — the one rule the sample needs: `type: SOCKET`, `listening: true`,
  `action: reopen`.
- `warm.sh N` — plain start against restore in alternation: readiness, the first `POST` after it,
  the container's memory at readiness and after 100 requests (`docker stats`), and how many JIT
  compilations (`-XX:+PrintCompilation` lines) happen during those 100 requests.

Results of 2026-09-11 (`results/`):

| | plain start | restore |
|---|---|---|
| `docker run` → first 200, median of 5 | 642 ms (543–953) / 639 ms (600–764) | 50 ms (45–52) / 82 ms (71–84) |
| first `POST /api/order` after readiness | 31 ms (25–44) | 12 ms (11–14) |
| container memory at readiness / after 100 requests | 70 / 100 MiB | 36 / 62 MiB |
| JIT compilations during the first 100 requests | 1555–1591 | 321–366 |
| image | — | 82 MB (`run.sh`), 85 MB (`warm.sh`, `-XX:+PrintCompilation` on) |

The two restore columns are the two scripts: `run.sh` restores an image taken without
`-XX:+PrintCompilation`, `warm.sh` with it. Without the policy file the checkpoint is refused:
`CheckpointOpenSocketException` for the `ServerSocketChannelImpl` and for the selector's epoll
descriptors "with registered keys" (`results/2026-09-11-crac-ktor-zulu25.0.4.1.log`). With the
one rule it succeeds, and the restored server answers `/api/order` and `/api/warm` with 200.
The same image restored under Zulu 25.0.4 (one build older, same major) fails — warp validates the
build-id of every mapped file at its checkpoint path — and under Zulu 26 it fails on the CPU
features check before anything else (`results/2026-09-11-crac-other-build-zulu25.0.4.1.log`).

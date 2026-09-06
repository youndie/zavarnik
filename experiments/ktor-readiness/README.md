# ktor-readiness

The stand behind B-01 and §1.9 of [the research](../../docs/research/research-architecture.md):
a plain Ktor application on the `application` plugin (`app/`), and a harness (`run.sh`) that
measures time-to-first-200 on `/health` from outside the process — the clock starts before the
start script is launched and stops on the first HTTP 200 — twenty runs per (cache, GC) variant.
It also counts how many classes the JVM served from the cache, and checks that the cache variant
really used it (`-XX:AOTMode=on`).

```bash
JAVA_HOME=/path/to/jdk-25 ./run.sh 20
```

`app/build.gradle.kts` carries the start-script guard the plugin will insert (research D2); the
harness normalises jar mtimes the way the plugin will (D3). Nothing here uses the plugin — it does
not exist yet — which is the point: the numbers say whether it should.

`results/` holds one log per run, named `<date>-<os>-<arch>-<jdk>-run<N>.log`. The first Linux
run of 2026-09-06 is not there: a one-way replica deleted the scratch logs mid-run, which is why
`run.sh` now writes them outside the tree.

# memory

What the AOT cache costs in resident memory, on the Ktor sample (`samples/ktor`, installed with
its cache): `run.sh N` starts the application without the cache (`-XX:AOTMode=off`) and with it,
in alternation, N times each, and reads `VmRSS`, `RssAnon` and `RssFile` from `/proc/<pid>/status`
twice — when `/health` first answers, and after 200 requests over the two sample routes. The
start script `exec`s `java`, so the script's pid is the JVM's. Medians of the five runs in
`results/2026-09-09-linux-x86_64-openjdk-25.0.4.log`, kB → MiB:

| | without the cache | with the cache |
|---|---|---|
| RSS at readiness | 86 984 kB (85.0 MiB), range 85 940–87 476 | 86 708 kB (84.7 MiB), range 85 400–88 716 |
| RSS after 200 requests | 125 848 kB (122.9 MiB), range 123 048–127 476 | 122 520 kB (119.6 MiB), range 118 068–133 676 |
| `RssFile`, either point | 22.0–22.2 MiB | 21.8–22.3 MiB |

The 31 MiB cache does not show up in resident memory: the medians differ by less than the
spread of either row, and the file-backed share is the same with and without it. What the cache
costs is disk — the file itself and its layer in the image — and the training run, whose second
JVM assembles the cache (JEP 514 says to plan on twice the heap for it).

```bash
./run.sh 5      # from the repository root, on a box where samples/ktor is installed and trained
```

# cpu-portability

B-09: does a cache trained on a CPU with AVX-512 crash on a CPU without it? The JVM enables
`AOTAdapterCaching` ergonomically whenever a cache is in use, and the cached adapters are
generated for the training machine; two public reports (Quarkus on Red Hat 25.0.3, Nucleus #400)
show `SIGILL` in `~AdapterBlob` on the narrower machine.

The pair here: training on an **AMD EPYC Genoa** (`UseAVX=3`, twelve `avx512*` flags), running on
an **Intel Core Ultra 7 255HX** (`UseAVX=2`, no AVX-512), both on the very same JDK build,
`25.0.4+7-1-24.04-Ubuntu`. Four caches: hello world and the Ktor sample, each trained natively
(adapters kept, `(Code)` region present, 326 / 495 AOT code entries) and portably
(`-XX:-AOTAdapterCaching`, no code region).

`train.sh` runs on the training box against an unpacked `b09/` tree (hello world jar and the two
`installDist` layouts of `samples/ktor`); `run.sh` runs on the production box against the caches
it produced. The logs are the result.

**Outcome: not reproduced.** All four caches load under `-XX:AOTMode=on` on the narrower CPU,
the native ones with their AOT code entries; hello world 20 of 20 runs, the sample ready with
20 of 20 application classes from the cache. This is a negative result *without a positive
control* — no machine at hand crashes — so it says the mitigation is not needed on this pair,
not that it is not needed. The default stays on because it costs nothing (research §1.6).

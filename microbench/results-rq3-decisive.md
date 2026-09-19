# RQ3, the decisive arms — 2026-09-19, bench-a, JDK 25.0.4, 3 forks x 5x2s

## escape analysis on (default)
Benchmark                                                    Mode  Cnt     Score    Error   Units
Continuations.allocatesOneInteger                            avgt   15     4.030 ±  0.060   ns/op
Continuations.allocatesOneInteger:gc.alloc.rate              avgt   15  3785.710 ± 55.976  MB/sec
Continuations.allocatesOneInteger:gc.alloc.rate.norm         avgt   15    16.000 ±  0.001    B/op
Continuations.allocatesOneInteger:gc.count                   avgt   15   350.000           counts
Continuations.allocatesOneInteger:gc.time                    avgt   15   557.000               ms
Continuations.megaVirtual                                    avgt   15  1025.401 ± 42.099   ns/op
Continuations.megaVirtual:gc.alloc.rate                      avgt   15     0.003 ±  0.001  MB/sec
Continuations.megaVirtual:gc.alloc.rate.norm                 avgt   15     0.004 ±  0.001    B/op
Continuations.megaVirtual:gc.count                           avgt   15       ≈ 0           counts
Continuations.monoVirtual                                    avgt   15   177.514 ±  5.176   ns/op
Continuations.monoVirtual:gc.alloc.rate                      avgt   15     0.003 ±  0.001  MB/sec
Continuations.monoVirtual:gc.alloc.rate.norm                 avgt   15     0.001 ±  0.001    B/op
Continuations.monoVirtual:gc.count                           avgt   15       ≈ 0           counts
Continuations.plainChain                                     avgt   15     0.917 ±  0.028   ns/op
Continuations.plainChain:gc.alloc.rate                       avgt   15     0.003 ±  0.001  MB/sec
Continuations.plainChain:gc.alloc.rate.norm                  avgt   15    ≈ 10⁻⁵             B/op
Continuations.plainChain:gc.count                            avgt   15       ≈ 0           counts
Continuations.suspendChainHoistedInt                         avgt   15     1.826 ±  0.056   ns/op
Continuations.suspendChainHoistedInt:gc.alloc.rate           avgt   15     0.003 ±  0.001  MB/sec
Continuations.suspendChainHoistedInt:gc.alloc.rate.norm      avgt   15    ≈ 10⁻⁵             B/op
Continuations.suspendChainHoistedInt:gc.count                avgt   15       ≈ 0           counts
Continuations.suspendReturningInt                            avgt   15     3.846 ±  0.092   ns/op
Continuations.suspendReturningInt:gc.alloc.rate              avgt   15  3968.007 ± 94.334  MB/sec
Continuations.suspendReturningInt:gc.alloc.rate.norm         avgt   15    16.000 ±  0.001    B/op
Continuations.suspendReturningInt:gc.count                   avgt   15   333.000           counts
Continuations.suspendReturningInt:gc.time                    avgt   15   551.000               ms
Continuations.suspendReturningIntUnboxed                     avgt   15     0.911 ±  0.035   ns/op
Continuations.suspendReturningIntUnboxed:gc.alloc.rate       avgt   15     0.003 ±  0.001  MB/sec
Continuations.suspendReturningIntUnboxed:gc.alloc.rate.norm  avgt   15    ≈ 10⁻⁵             B/op
Continuations.suspendReturningIntUnboxed:gc.count            avgt   15       ≈ 0           counts
Benchmark result is saved to /root/jmh-rq3-unit.json

## the same arm with -XX:-DoEscapeAnalysis
Continuations.suspendChainHoisted                     avgt   15    37.183 ±   1.960   ns/op
Continuations.suspendChainHoisted:gc.alloc.rate.norm  avgt   15   168.000 ±   0.001    B/op

## the value inside the Integer cache still allocates: Boxing.boxInt is new Integer(i)
Continuations.suspendChainHoistedCached                     avgt   15     4.335 ±   0.298   ns/op
Continuations.suspendChainHoistedCached:gc.alloc.rate       avgt   15  3531.798 ± 236.029  MB/sec
Continuations.suspendChainHoistedCached:gc.alloc.rate.norm  avgt   15    16.000 ±   0.001    B/op
Continuations.suspendChainHoistedCached:gc.count            avgt   15   359.000            counts
Continuations.suspendChainHoistedCached:gc.time             avgt   15   498.000                ms

## which class: async-profiler alloc, 100% of bytes
--- 20662674957 bytes (100.00%), 39411 samples
  [ 0] java.lang.Integer
  [ 1] kotlin.coroutines.jvm.internal.Boxing.boxInt
  [ 2] micro.ContinuationsKt.chainSuspend

# 2026-09-19T18:49:06Z label=candidates forks=3 warmup=5x2s measure=5x2s
# host: 4 cores,: 0.38, 0.91, 1.02, steal 0
# openjdk version "25.0.4" 2026-07-21

Benchmark                           Mode  Cnt     Score    Error  Units
ArrayIteration.direct               avgt   15   406.712 ± 15.638  ns/op
ArrayIteration.directTimesTwo       avgt   15    77.255 ±  3.301  ns/op
ArrayIteration.indexedOverIndices   avgt   15   396.101 ±  5.688  ns/op
ArrayIteration.indexedOverSize      avgt   15   396.860 ±  6.134  ns/op
Continuations.bi                    avgt   15   330.766 ± 17.364  ns/op
Continuations.mega                  avgt   15  1719.148 ± 87.151  ns/op
Continuations.mono                  avgt   15   193.284 ±  8.724  ns/op
Continuations.plainChain            avgt   15     0.701 ±  0.031  ns/op
Continuations.plainReturningInt     avgt   15     0.702 ±  0.040  ns/op
Continuations.suspendChainFastPath  avgt   15     3.936 ±  0.057  ns/op
Continuations.suspendReturningInt   avgt   15     4.007 ±  0.159  ns/op
Benchmark result is saved to /root/jmh-candidates.json

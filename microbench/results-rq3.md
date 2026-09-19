# 2026-09-19T19:37:32Z label=rq3c forks=3 warmup=5x2s measure=5x2s
# host: 4 cores,: 0.11, 0.23, 0.44, steal 0
# openjdk version "25.0.4" 2026-07-21

Benchmark                                              Mode  Cnt     Score     Error   Units
Continuations.plainChain                               avgt   15     0.943 ±   0.045   ns/op
Continuations.plainChain:gc.alloc.rate.norm            avgt   15    ≈ 10⁻⁵              B/op
Continuations.plainReturningInt                        avgt   15     0.957 ±   0.044   ns/op
Continuations.plainReturningInt:gc.alloc.rate.norm     avgt   15    ≈ 10⁻⁵              B/op
Continuations.suspendChainFastPath                     avgt   15     3.745 ±   0.058   ns/op
Continuations.suspendChainFastPath:gc.alloc.rate.norm  avgt   15    16.000 ±   0.001    B/op
Continuations.suspendChainHoisted                      avgt   15     4.327 ±   0.202   ns/op
Continuations.suspendChainHoisted:gc.alloc.rate.norm   avgt   15    16.000 ±   0.001    B/op
Continuations.suspendReturningInt                      avgt   15     3.859 ±   0.072   ns/op
Continuations.suspendReturningInt:gc.alloc.rate.norm   avgt   15    16.000 ±   0.001    B/op
Benchmark result is saved to /root/jmh-rq3c.json

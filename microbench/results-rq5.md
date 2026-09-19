# 2026-09-19T20:57:52Z label=rq5 forks=3 warmup=5x2s measure=5x2s
# host: 4 cores,: 0.21, 0.41, 1.01, steal 0
# openjdk version "25.0.4" 2026-07-21
Benchmark                                       Mode  Cnt     Score     Error   Units
Codegen.capturingLambda                         avgt   15   302.910 ±  16.563   ns/op
Codegen.capturingLambda:gc.alloc.rate           avgt   15     0.003 ±   0.001  MB/sec
Codegen.capturingLambda:gc.alloc.rate.norm      avgt   15     0.001 ±   0.001    B/op
Codegen.capturingLambda:gc.count                avgt   15       ≈ 0            counts
Codegen.collectionChain                         avgt   15  4592.473 ± 155.261   ns/op
Codegen.collectionChain:gc.alloc.rate           avgt   15  1492.768 ±  50.827  MB/sec
Codegen.collectionChain:gc.alloc.rate.norm      avgt   15  7184.016 ±   0.001    B/op
Codegen.collectionChain:gc.count                avgt   15   218.000            counts
Codegen.collectionChain:gc.time                 avgt   15   329.000                ms
Codegen.defaultArgs                             avgt   15     0.997 ±   0.148   ns/op
Codegen.defaultArgs:gc.alloc.rate               avgt   15     0.003 ±   0.001  MB/sec
Codegen.defaultArgs:gc.alloc.rate.norm          avgt   15    ≈ 10⁻⁵              B/op
Codegen.defaultArgs:gc.count                    avgt   15       ≈ 0            counts
Codegen.delegatedLazy                           avgt   15     1.995 ±   0.293   ns/op
Codegen.delegatedLazy:gc.alloc.rate             avgt   15     0.003 ±   0.001  MB/sec
Codegen.delegatedLazy:gc.alloc.rate.norm        avgt   15    ≈ 10⁻⁵              B/op
Codegen.delegatedLazy:gc.count                  avgt   15       ≈ 0            counts
Codegen.delegatedObservable                     avgt   15    14.289 ±   0.725   ns/op
Codegen.delegatedObservable:gc.alloc.rate       avgt   15  1069.657 ±  50.828  MB/sec
Codegen.delegatedObservable:gc.alloc.rate.norm  avgt   15    16.000 ±   0.001    B/op
Codegen.delegatedObservable:gc.count            avgt   15   157.000            counts
Codegen.delegatedObservable:gc.time             avgt   15   233.000                ms
Codegen.explicitArgs                            avgt   15     0.925 ±   0.059   ns/op
Codegen.explicitArgs:gc.alloc.rate              avgt   15     0.003 ±   0.001  MB/sec
Codegen.explicitArgs:gc.alloc.rate.norm         avgt   15    ≈ 10⁻⁵              B/op
Codegen.explicitArgs:gc.count                   avgt   15       ≈ 0            counts
Codegen.handWrittenApply                        avgt   15   305.472 ±  14.723   ns/op
Codegen.handWrittenApply:gc.alloc.rate          avgt   15     0.003 ±   0.001  MB/sec
Codegen.handWrittenApply:gc.alloc.rate.norm     avgt   15     0.001 ±   0.001    B/op
Codegen.handWrittenApply:gc.count               avgt   15       ≈ 0            counts
Codegen.handWrittenChain                        avgt   15  1216.441 ±  47.756   ns/op
Codegen.handWrittenChain:gc.alloc.rate          avgt   15     0.003 ±   0.001  MB/sec
Codegen.handWrittenChain:gc.alloc.rate.norm     avgt   15     0.004 ±   0.001    B/op
Codegen.handWrittenChain:gc.count               avgt   15       ≈ 0            counts
Codegen.plainProperty                           avgt   15     0.948 ±   0.027   ns/op
Codegen.plainProperty:gc.alloc.rate             avgt   15     0.003 ±   0.001  MB/sec
Codegen.plainProperty:gc.alloc.rate.norm        avgt   15    ≈ 10⁻⁵              B/op
Codegen.plainProperty:gc.count                  avgt   15       ≈ 0            counts
Codegen.rawIntGeneric                           avgt   15     0.865 ±   0.027   ns/op
Codegen.rawIntGeneric:gc.alloc.rate             avgt   15     0.003 ±   0.001  MB/sec
Codegen.rawIntGeneric:gc.alloc.rate.norm        avgt   15    ≈ 10⁻⁶              B/op
Codegen.rawIntGeneric:gc.count                  avgt   15       ≈ 0            counts
Codegen.sequenceChain                           avgt   15  1749.616 ±  98.961   ns/op
Codegen.sequenceChain:gc.alloc.rate             avgt   15  2237.467 ± 120.649  MB/sec
Codegen.sequenceChain:gc.alloc.rate.norm        avgt   15  4096.006 ±   0.001    B/op
Codegen.sequenceChain:gc.count                  avgt   15   284.000            counts
Codegen.sequenceChain:gc.time                   avgt   15   474.000                ms
Codegen.valueClassDirect                        avgt   15     0.848 ±   0.019   ns/op
Codegen.valueClassDirect:gc.alloc.rate          avgt   15     0.003 ±   0.001  MB/sec
Codegen.valueClassDirect:gc.alloc.rate.norm     avgt   15    ≈ 10⁻⁶              B/op
Codegen.valueClassDirect:gc.count               avgt   15       ≈ 0            counts
Codegen.valueClassGeneric                       avgt   15     0.866 ±   0.029   ns/op
Codegen.valueClassGeneric:gc.alloc.rate         avgt   15     0.003 ±   0.001  MB/sec
Codegen.valueClassGeneric:gc.alloc.rate.norm    avgt   15    ≈ 10⁻⁶              B/op
Codegen.valueClassGeneric:gc.count              avgt   15       ≈ 0            counts
Codegen.valueClassNullable                      avgt   15     0.866 ±   0.023   ns/op
Codegen.valueClassNullable:gc.alloc.rate        avgt   15     0.003 ±   0.001  MB/sec
Codegen.valueClassNullable:gc.alloc.rate.norm   avgt   15    ≈ 10⁻⁶              B/op
Codegen.valueClassNullable:gc.count             avgt   15       ≈ 0            counts
Benchmark result is saved to /root/jmh-rq5.json

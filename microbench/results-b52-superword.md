# B-52: is the multiply arm's advantage vectorisation? — 2026-09-20, bench-a, JDK 25.0.4
# 3 forks x 5x2s. -XX:-UseSuperWord is the differential: no hsdis needed.

## SuperWord on (default)
Benchmark                          Mode  Cnt    Score   Error  Units
ArrayIteration.direct              avgt   15  389.731 ± 3.976  ns/op
ArrayIteration.directTimesTwo      avgt   15   71.092 ± 2.285  ns/op
ArrayIteration.indexedOverIndices  avgt   15  391.233 ± 8.655  ns/op
ArrayIteration.indexedOverSize     avgt   15  388.973 ± 5.465  ns/op
Benchmark result is saved to /root/jmh-sw-on.json

## -XX:-UseSuperWord
ArrayIteration.direct              avgt   15  387.721 ± 5.263  ns/op
ArrayIteration.directTimesTwo      avgt   15  400.701 ± 6.478  ns/op
ArrayIteration.indexedOverIndices  avgt   15  388.962 ± 4.574  ns/op
ArrayIteration.indexedOverSize     avgt   15  388.527 ± 4.704  ns/op

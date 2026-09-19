# 2026-09-19T18:34:34Z label=controls forks=3 warmup=5x2s measure=5x2s
# host: 4 cores,: 1.14, 1.13, 1.09, steal 0
# openjdk version "25.0.4" 2026-07-21

Benchmark                   Mode  Cnt     Score     Error  Units
Controls.checkedParam       avgt   15     1.670 ±   0.062  ns/op
Controls.dataAccessors      avgt   15     1.335 ±   0.042  ns/op
Controls.dataCopy           avgt   15     5.354 ±   0.119  ns/op
Controls.forOverRange       avgt   15   393.549 ±   3.935  ns/op
Controls.handWrittenLoop    avgt   15    74.899 ±   2.943  ns/op
Controls.inlineLambda       avgt   15    76.054 ±   2.493  ns/op
Controls.intSwitch          avgt   15  1916.949 ± 159.917  ns/op
Controls.knownOrderBase     avgt   15   179.708 ±  12.705  ns/op
Controls.knownOrderPlusOne  avgt   15   269.522 ±   8.992  ns/op
Controls.plainCopy          avgt   15     5.593 ±   0.151  ns/op
Controls.sealedWhen         avgt   15  1999.488 ±  58.703  ns/op
Controls.uncheckedParam     avgt   15     1.863 ±   0.397  ns/op
Controls.whileLoop          avgt   15   435.220 ±  38.184  ns/op
Benchmark result is saved to /root/jmh-controls.json

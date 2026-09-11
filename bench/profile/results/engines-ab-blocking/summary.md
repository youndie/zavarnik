# 2026-09-11T01:39:58Z label=engines-ab-blocking variants='cio cio@-Dkotlinx.coroutines.io.parallelism=8 netty jetty' reps=3 warmup=15s measure=30s conns=64
# OpenJDK Runtime Environment (build 25.0.4+7-1-24.04-Ubuntu)
cio rep1 blocking: rps=11818 p50=5.38ms p99=5.92ms ok=100.0% cpu=150us/req cores=1.77
cio rep1 peak rss 765.625 MiB
cio__Dkotlinx_coroutines_io_parallelism_8 rep1 blocking: rps=1538 p50=37.90ms p99=64.08ms ok=100.0% cpu=145us/req cores=0.22
cio@-Dkotlinx.coroutines.io.parallelism=8 rep1 peak rss 574.062 MiB
netty rep1 blocking: rps=1552 p50=41.24ms p99=41.93ms ok=100.0% cpu=175us/req cores=0.27
netty rep1 peak rss 552.656 MiB
jetty rep1 blocking: rps=11951 p50=5.32ms p99=5.77ms ok=100.0% cpu=138us/req cores=1.65
jetty rep1 peak rss 791.562 MiB
cio rep2 blocking: rps=11827 p50=5.38ms p99=5.86ms ok=100.0% cpu=149us/req cores=1.76
cio rep2 peak rss 769.988 MiB
cio__Dkotlinx_coroutines_io_parallelism_8 rep2 blocking: rps=1538 p50=37.89ms p99=66.78ms ok=100.0% cpu=151us/req cores=0.23
cio@-Dkotlinx.coroutines.io.parallelism=8 rep2 peak rss 621.875 MiB
netty rep2 blocking: rps=1552 p50=41.23ms p99=42.03ms ok=100.0% cpu=160us/req cores=0.25
netty rep2 peak rss 584.844 MiB
jetty rep2 blocking: rps=11875 p50=5.34ms p99=6.03ms ok=100.0% cpu=152us/req cores=1.81
jetty rep2 peak rss 788.895 MiB
cio rep3 blocking: rps=11476 p50=5.42ms p99=8.07ms ok=100.0% cpu=166us/req cores=1.90
cio rep3 peak rss 759.531 MiB
cio__Dkotlinx_coroutines_io_parallelism_8 rep3 blocking: rps=1537 p50=37.83ms p99=67.61ms ok=100.0% cpu=148us/req cores=0.23
cio@-Dkotlinx.coroutines.io.parallelism=8 rep3 peak rss 626.094 MiB
netty rep3 blocking: rps=1552 p50=41.24ms p99=41.92ms ok=100.0% cpu=162us/req cores=0.25
netty rep3 peak rss 591.875 MiB
jetty rep3 blocking: rps=11945 p50=5.32ms p99=5.79ms ok=100.0% cpu=142us/req cores=1.69
jetty rep3 peak rss 799.832 MiB
## medians over 3 reps
| endpoint | variant | rps median (runs) | p50 | p99 | us cpu/req | cores busy |
|---|---|---|---|---|---|---|
| blocking | cio | 11818 (11476 11818 11827) | 5.38 ms | 5.92 ms | 150 (149 150 166) | 1.77 |
| blocking | cio@-Dkotlinx.coroutines.io.parallelism=8 | 1538 (1537 1538 1538) | 37.89 ms | 66.78 ms | 148 (145 148 151) | 0.23 |
| blocking | netty | 1552 (1552 1552 1552) | 41.24 ms | 41.93 ms | 162 (160 162 175) | 0.25 |
| blocking | jetty | 11945 (11875 11945 11951) | 5.32 ms | 5.79 ms | 142 (138 142 152) | 1.69 |

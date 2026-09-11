# 2026-09-11T01:19:17Z label=engines-ab-parallelism variants='cio cio@-Dkotlinx.coroutines.io.parallelism=8 cio@-Dkotlinx.coroutines.io.parallelism=16' reps=3 warmup=30s measure=60s conns=64
# OpenJDK Runtime Environment (build 25.0.4+7-1-24.04-Ubuntu)
cio rep1 business: rps=42909 p50=0.84ms p99=14.14ms ok=100.0% cpu=169us/req cores=7.27
cio rep1 peak rss 809.52 MiB
cio__Dkotlinx_coroutines_io_parallelism_8 rep1 business: rps=69130 p50=0.86ms p99=2.45ms ok=100.0% cpu=98us/req cores=6.79
cio@-Dkotlinx.coroutines.io.parallelism=8 rep1 peak rss 796.387 MiB
cio__Dkotlinx_coroutines_io_parallelism_16 rep1 business: rps=68382 p50=0.74ms p99=6.22ms ok=100.0% cpu=105us/req cores=7.18
cio@-Dkotlinx.coroutines.io.parallelism=16 rep1 peak rss 800.762 MiB
cio rep2 business: rps=40536 p50=0.89ms p99=14.45ms ok=100.0% cpu=176us/req cores=7.13
cio rep2 peak rss 803.902 MiB
cio__Dkotlinx_coroutines_io_parallelism_8 rep2 business: rps=67175 p50=0.87ms p99=2.52ms ok=100.0% cpu=99us/req cores=6.63
cio@-Dkotlinx.coroutines.io.parallelism=8 rep2 peak rss 797.02 MiB
cio__Dkotlinx_coroutines_io_parallelism_16 rep2 business: rps=66003 p50=0.77ms p99=5.72ms ok=100.0% cpu=107us/req cores=7.09
cio@-Dkotlinx.coroutines.io.parallelism=16 rep2 peak rss 791.562 MiB
cio rep3 business: rps=40673 p50=0.87ms p99=14.64ms ok=100.0% cpu=177us/req cores=7.19
cio rep3 peak rss 800.926 MiB
cio__Dkotlinx_coroutines_io_parallelism_8 rep3 business: rps=72691 p50=0.82ms p99=2.19ms ok=100.0% cpu=92us/req cores=6.70
cio@-Dkotlinx.coroutines.io.parallelism=8 rep3 peak rss 793.281 MiB
cio__Dkotlinx_coroutines_io_parallelism_16 rep3 business: rps=70383 p50=0.72ms p99=5.90ms ok=100.0% cpu=102us/req cores=7.15
cio@-Dkotlinx.coroutines.io.parallelism=16 rep3 peak rss 792.305 MiB
## medians over 3 reps
| endpoint | variant | rps median (runs) | p50 | p99 | us cpu/req | cores busy |
|---|---|---|---|---|---|---|
| business | cio | 40673 (40536 40673 42909) | 0.87 ms | 14.45 ms | 176 (169 176 177) | 7.19 |
| business | cio@-Dkotlinx.coroutines.io.parallelism=8 | 69130 (67175 69130 72691) | 0.86 ms | 2.45 ms | 98 (92 98 99) | 6.70 |
| business | cio@-Dkotlinx.coroutines.io.parallelism=16 | 68382 (66003 68382 70383) | 0.74 ms | 5.90 ms | 105 (102 105 107) | 7.15 |

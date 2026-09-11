# 2026-09-10T23:29:49Z label=engines-ab variants='cio netty jetty' reps=3 warmup=30s measure=60s conns=64
# OpenJDK Runtime Environment (build 25.0.4+7-1-24.04-Ubuntu)
cio rep1 echo: rps=54865 p50=0.72ms p99=11.18ms ok=100.0% cpu=126us/req cores=6.89
cio rep1 items: rps=48615 p50=0.87ms p99=9.86ms ok=100.0% cpu=145us/req cores=7.06
cio rep1 business: rps=42766 p50=0.86ms p99=13.57ms ok=100.0% cpu=168us/req cores=7.17
cio rep1 peak rss 809.062 MiB
netty rep1 echo: rps=87146 p50=0.67ms p99=1.84ms ok=100.0% cpu=64us/req cores=5.54
netty rep1 items: rps=57515 p50=1.02ms p99=2.71ms ok=100.0% cpu=103us/req cores=5.93
netty rep1 business: rps=62650 p50=0.90ms p99=3.10ms ok=100.0% cpu=98us/req cores=6.12
netty rep1 peak rss 849.844 MiB
jetty rep1 echo: rps=71571 p50=0.77ms p99=2.90ms ok=100.0% cpu=101us/req cores=7.23
jetty rep1 items: rps=59181 p50=0.95ms p99=3.13ms ok=100.0% cpu=123us/req cores=7.31
jetty rep1 business: rps=52121 p50=1.14ms p99=2.94ms ok=100.0% cpu=147us/req cores=7.64
jetty rep1 peak rss 924.039 MiB
cio rep2 echo: rps=53377 p50=0.74ms p99=11.77ms ok=100.0% cpu=129us/req cores=6.90
cio rep2 items: rps=49781 p50=0.85ms p99=9.83ms ok=100.0% cpu=142us/req cores=7.08
cio rep2 business: rps=41365 p50=0.86ms p99=14.44ms ok=100.0% cpu=174us/req cores=7.22
cio rep2 peak rss 815 MiB
netty rep2 echo: rps=101078 p50=0.58ms p99=1.48ms ok=100.0% cpu=56us/req cores=5.67
netty rep2 items: rps=59197 p50=0.98ms p99=2.84ms ok=100.0% cpu=94us/req cores=5.57
netty rep2 business: rps=72181 p50=0.80ms p99=2.25ms ok=100.0% cpu=89us/req cores=6.40
netty rep2 peak rss 864.504 MiB
jetty rep2 echo: rps=73099 p50=0.76ms p99=2.78ms ok=100.0% cpu=99us/req cores=7.23
jetty rep2 items: rps=53226 p50=1.05ms p99=3.62ms ok=100.0% cpu=136us/req cores=7.26
jetty rep2 business: rps=46604 p50=1.24ms p99=3.76ms ok=100.0% cpu=164us/req cores=7.63
jetty rep2 peak rss 933.438 MiB
cio rep3 echo: rps=51124 p50=0.77ms p99=11.41ms ok=100.0% cpu=133us/req cores=6.80
cio rep3 items: rps=49743 p50=0.87ms p99=9.62ms ok=100.0% cpu=143us/req cores=7.09
cio rep3 business: rps=42046 p50=0.87ms p99=13.95ms ok=100.0% cpu=171us/req cores=7.18
cio rep3 peak rss 831.25 MiB
netty rep3 echo: rps=98730 p50=0.60ms p99=1.50ms ok=100.0% cpu=58us/req cores=5.71
netty rep3 items: rps=68919 p50=0.85ms p99=2.16ms ok=100.0% cpu=86us/req cores=5.95
netty rep3 business: rps=80566 p50=0.73ms p99=1.83ms ok=100.0% cpu=82us/req cores=6.59
netty rep3 peak rss 860.582 MiB
jetty rep3 echo: rps=82327 p50=0.68ms p99=2.47ms ok=100.0% cpu=89us/req cores=7.32
jetty rep3 items: rps=60374 p50=0.94ms p99=3.06ms ok=100.0% cpu=121us/req cores=7.31
jetty rep3 business: rps=52754 p50=1.13ms p99=2.90ms ok=100.0% cpu=145us/req cores=7.64
jetty rep3 peak rss 927.969 MiB
## medians over 3 reps
| endpoint | variant | rps median (runs) | p50 | p99 | us cpu/req | cores busy |
|---|---|---|---|---|---|---|
| echo | cio | 53377 (51124 53377 54865) | 0.74 ms | 11.41 ms | 129 (126 129 133) | 6.89 |
| echo | netty | 98730 (87146 98730 101078) | 0.60 ms | 1.50 ms | 58 (56 58 64) | 5.67 |
| echo | jetty | 73099 (71571 73099 82327) | 0.76 ms | 2.78 ms | 99 (89 99 101) | 7.23 |
| items | cio | 49743 (48615 49743 49781) | 0.87 ms | 9.83 ms | 143 (142 143 145) | 7.08 |
| items | netty | 59197 (57515 59197 68919) | 0.98 ms | 2.71 ms | 94 (86 94 103) | 5.93 |
| items | jetty | 59181 (53226 59181 60374) | 0.95 ms | 3.13 ms | 123 (121 123 136) | 7.31 |
| business | cio | 42046 (41365 42046 42766) | 0.86 ms | 13.95 ms | 171 (168 171 174) | 7.18 |
| business | netty | 72181 (62650 72181 80566) | 0.80 ms | 2.25 ms | 89 (82 89 98) | 6.40 |
| business | jetty | 52121 (46604 52121 52754) | 1.14 ms | 2.94 ms | 147 (145 147 164) | 7.64 |

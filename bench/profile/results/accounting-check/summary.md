# Проверка прибора на машине стенда (research-engines §1.14)
# Потиковый счётчик процесса против занятости закреплённых ядер 0-7 из /proc/stat.

=== 2026-09-11T09:05:11Z stand machine, accounting check, cores 0-7
  idle: 1.11 cores busy of 8
### variant=io default
  accounting: wall=26.2s proc_ticks=204.51s cores0-7_busy=192.29s proc/cpus=1.064 cores_by_proc=7.80 cores_by_cpus=7.33 ctx=1410370 (53777/s)
  variant=io io.parallelism=default processors=8 concurrency=64 hops=4 ops=3390979 ops_per_s=168895 us_cpu_per_op=50.25 us_cpu_per_hop=12.56 cores=8.49 threads=109
### variant=io -Dkotlinx.coroutines.io.parallelism=8
  accounting: wall=26.1s proc_ticks=203.19s cores0-7_busy=189.57s proc/cpus=1.072 cores_by_proc=7.79 cores_by_cpus=7.27 ctx=7760215 (297661/s)
  variant=io io.parallelism=8 processors=8 concurrency=64 hops=4 ops=14099101 ops_per_s=704684 us_cpu_per_op=12.02 us_cpu_per_hop=3.01 cores=8.47 threads=29
### variant=default default
  accounting: wall=23.9s proc_ticks=204.08s cores0-7_busy=191.00s proc/cpus=1.068 cores_by_proc=8.54 cores_by_cpus=8.00 ctx=598 (25/s)
  variant=default io.parallelism=default processors=8 concurrency=64 hops=4 ops=1037160982 ops_per_s=51831421 us_cpu_per_op=0.17 us_cpu_per_hop=0.04 cores=8.56 threads=14
=== 2026-09-11T09:06:36Z done6

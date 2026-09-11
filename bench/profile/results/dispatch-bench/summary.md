# DispatchBench — цена одного диспатча, без Ktor (research-engines §1.14, §1.15)
# Машина: запасная машина владельца, 2 ядра, ядро 6.8, OpenJDK 25.0.4, CONFIG_SCHEDSTATS=y.
# Каждый прогон: 5 с прогрева, 20 с замера, 64 корутины, 4 yield() на операцию.
# Строка accounting снята вокруг всего процесса: потиковая оценка против sum_exec_runtime.

=== 2026-09-11T08:15:25Z the owner.s spare box: 2 cores, kernel 6.8.0-138-generic, java openjdk version "25.0.4" 2026-07-21
--- idle check
  background: 0.22 cores busy of 2
### variant=io default
  accounting: wall=23.7s ticks=41.94s exec=42.04s ticks/exec=0.998 ctx=360784 (15212/s) cores_by_ticks=1.77 cores_by_exec=1.77
  variant=io io.parallelism=default processors=2 concurrency=64 hops=4 ops=4432972 ops_per_s=219816 us_cpu_per_op=8.04 us_cpu_per_hop=2.01 cores=1.77 threads=114
### variant=io -Dkotlinx.coroutines.io.parallelism=2
  accounting: wall=23.7s ticks=42.01s exec=42.00s ticks/exec=1.000 ctx=2443606 (102892/s) cores_by_ticks=1.77 cores_by_exec=1.77
  variant=io io.parallelism=2 processors=2 concurrency=64 hops=4 ops=51646882 ops_per_s=2581194 us_cpu_per_op=0.68 us_cpu_per_hop=0.17 cores=1.77 threads=15
### variant=io -Dkotlinx.coroutines.io.parallelism=8
  accounting: wall=24.0s ticks=43.32s exec=43.33s ticks/exec=1.000 ctx=1075611 (44752/s) cores_by_ticks=1.80 cores_by_exec=1.80
  variant=io io.parallelism=8 processors=2 concurrency=64 hops=4 ops=36072499 ops_per_s=1802299 us_cpu_per_op=1.00 us_cpu_per_hop=0.25 cores=1.80 threads=25
### variant=io-view-2 default
  accounting: wall=23.7s ticks=41.99s exec=41.98s ticks/exec=1.000 ctx=2922750 (123088/s) cores_by_ticks=1.77 cores_by_exec=1.77
  variant=io-view-2 io.parallelism=default processors=2 concurrency=64 hops=4 ops=61517779 ops_per_s=3071939 us_cpu_per_op=0.57 us_cpu_per_hop=0.14 cores=1.76 threads=15
### variant=io-view-8 default
  accounting: wall=23.7s ticks=41.99s exec=41.96s ticks/exec=1.001 ctx=1104524 (46605/s) cores_by_ticks=1.77 cores_by_exec=1.77
  variant=io-view-8 io.parallelism=default processors=2 concurrency=64 hops=4 ops=36002924 ops_per_s=1797108 us_cpu_per_op=0.99 us_cpu_per_hop=0.25 cores=1.78 threads=24
### variant=default default
  accounting: wall=24.0s ticks=42.54s exec=42.54s ticks/exec=1.000 ctx=68912 (2876/s) cores_by_ticks=1.78 cores_by_exec=1.78
  variant=default io.parallelism=default processors=2 concurrency=64 hops=4 ops=175667495 ops_per_s=8773056 us_cpu_per_op=0.20 us_cpu_per_hop=0.05 cores=1.77 threads=8
=== 2026-09-11T08:18:03Z done


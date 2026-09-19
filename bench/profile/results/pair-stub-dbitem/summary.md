# 2026-09-19T14:09:08Z label=pair-stub-dbitem warmup=45s measure=60s conns=64
# OpenJDK Runtime Environment (build 25.0.4+7-1-26.04-Ubuntu)
# model name	: Intel Xeon Processor (Skylake, IBRS, no TSX)
## dbitem
clean: rps=2131 p50=0.82ms p99=31.31ms requests=100.0% ok
cost: cpu=57.1s over 60.0s = 0.95 cores busy, 447 us cpu/req, 2.22 ctx/req, threads=32, peak rss=769 MiB, requests=127857
rps=2131 p50=0.83ms p99=4.33ms requests=100.0% ok
## /root/bench-results/pair-stub-dbitem/dbitem.cpu.collapsed  (samples: 38795)
| category | self | owner |
|---|---|---|
| user |   0.3% |   0.3% |
| ktor |   9.7% |  14.3% |
| kotlinx |   5.5% |   7.2% |
| kotlin |   4.6% |   6.7% |
| jdk |  12.5% |   0.0% |
| other |  16.8% |  61.9% |
| jvm |  50.6% |   9.6% |
| user code anywhere on the stack |  13.7% | |

cpu self:   6.5% _raw_spin_unlock_irq_[k]
cpu self:   5.0% __syscall_cancel_arch_end
cpu self:   3.6% finish_task_switch.isra.0_[k]
cpu self:   2.7% do_syscall_64_[k]
cpu self:   2.0% iowrite16_[k]
## GC and JIT from the service log
lines=8
sat_rate=2131

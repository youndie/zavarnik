# 2026-09-19T14:26:36Z label=pair-real-dbpost warmup=45s measure=60s conns=64
# OpenJDK Runtime Environment (build 25.0.4+7-1-26.04-Ubuntu)
# model name	: Intel Xeon Processor (Skylake, IBRS, no TSX)
## dbpost
clean: rps=1635 p50=4.55ms p99=117.29ms requests=100.0% ok
cost: cpu=108.7s over 60.0s = 1.81 cores busy, 1108 us cpu/req, 10.03 ctx/req, threads=101, peak rss=850 MiB, requests=98098
rps=1635 p50=3.71ms p99=40.83ms requests=100.0% ok
## /root/bench-results/pair-real-dbpost/dbpost.cpu.collapsed  (samples: 77475)
| category | self | owner |
|---|---|---|
| user |   0.4% |   0.4% |
| ktor |   4.3% |   6.5% |
| kotlinx |   7.4% |  14.0% |
| kotlin |   5.8% |  10.2% |
| jdk |  14.2% |   0.1% |
| other |  17.1% |  62.3% |
| jvm |  50.9% |   6.5% |
| user code anywhere on the stack |  48.9% | |

cpu self:   5.0% finish_task_switch.isra.0_[k]
cpu self:   4.2% __syscall_cancel_arch_end
cpu self:   4.0% _raw_spin_unlock_irqrestore_[k]
cpu self:   2.7% _raw_spin_unlock_irq_[k]
cpu self:   2.6% itable stub
## GC and JIT from the service log
lines=11
sat_rate=1635

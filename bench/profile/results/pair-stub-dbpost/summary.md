# 2026-09-19T14:23:53Z label=pair-stub-dbpost warmup=45s measure=60s conns=64
# OpenJDK Runtime Environment (build 25.0.4+7-1-26.04-Ubuntu)
# model name	: Intel Xeon Processor (Skylake, IBRS, no TSX)
## dbpost
clean: rps=1635 p50=0.89ms p99=47.09ms requests=100.0% ok
cost: cpu=63.5s over 60.0s = 1.06 cores busy, 647 us cpu/req, 2.55 ctx/req, threads=32, peak rss=799 MiB, requests=98098
rps=1635 p50=0.87ms p99=4.03ms requests=100.0% ok
## /root/bench-results/pair-stub-dbpost/dbpost.cpu.collapsed  (samples: 39472)
| category | self | owner |
|---|---|---|
| user |   0.3% |   0.3% |
| ktor |   8.5% |  12.6% |
| kotlinx |   7.6% |  10.2% |
| kotlin |   6.4% |  10.3% |
| jdk |  12.3% |   0.0% |
| other |  15.6% |  52.8% |
| jvm |  49.2% |  13.7% |
| user code anywhere on the stack |  19.5% | |

cpu self:   5.2% _raw_spin_unlock_irq_[k]
cpu self:   4.1% __syscall_cancel_arch_end
cpu self:   3.1% finish_task_switch.isra.0_[k]
cpu self:   2.0% do_syscall_64_[k]
cpu self:   1.7% itable stub
## GC and JIT from the service log
lines=8
sat_rate=1635

# 2026-09-19T14:11:51Z label=pair-real-dbitem warmup=45s measure=60s conns=64
# OpenJDK Runtime Environment (build 25.0.4+7-1-26.04-Ubuntu)
# model name	: Intel Xeon Processor (Skylake, IBRS, no TSX)
## dbitem
clean: rps=2131 p50=6.22ms p99=73.96ms requests=100.0% ok
cost: cpu=127.2s over 60.0s = 2.12 cores busy, 995 us cpu/req, 8.96 ctx/req, threads=101, peak rss=836 MiB, requests=127857
rps=2131 p50=2.28ms p99=38.34ms requests=100.0% ok
## /root/bench-results/pair-real-dbitem/dbitem.cpu.collapsed  (samples: 84270)
| category | self | owner |
|---|---|---|
| user |   0.3% |   0.3% |
| ktor |   4.1% |   6.0% |
| kotlinx |   6.7% |  13.3% |
| kotlin |   4.7% |   8.3% |
| jdk |  12.5% |   0.1% |
| other |  18.8% |  66.6% |
| jvm |  53.1% |   5.4% |
| user code anywhere on the stack |  47.9% | |

cpu self:   5.5% finish_task_switch.isra.0_[k]
cpu self:   4.7% _raw_spin_unlock_irqrestore_[k]
cpu self:   4.6% __syscall_cancel_arch_end
cpu self:   2.9% do_syscall_64_[k]
cpu self:   2.9% _raw_spin_unlock_irq_[k]
## GC and JIT from the service log
lines=11
sat_rate=2131

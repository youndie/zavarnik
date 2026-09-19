# 2026-09-19T14:16:31Z label=pair-stub-dblist warmup=45s measure=60s conns=64
# OpenJDK Runtime Environment (build 25.0.4+7-1-26.04-Ubuntu)
# model name	: Intel Xeon Processor (Skylake, IBRS, no TSX)
## dblist
clean: rps=1818 p50=0.91ms p99=36.48ms requests=100.0% ok
cost: cpu=61.2s over 60.0s = 1.02 cores busy, 561 us cpu/req, 2.36 ctx/req, threads=32, peak rss=773 MiB, requests=109079
rps=1818 p50=0.90ms p99=4.97ms requests=100.0% ok
## /root/bench-results/pair-stub-dblist/dblist.cpu.collapsed  (samples: 40599)
| category | self | owner |
|---|---|---|
| user |   0.4% |   0.4% |
| ktor |   7.0% |  12.0% |
| kotlinx |  13.3% |  17.9% |
| kotlin |   4.0% |   6.2% |
| jdk |  13.7% |   0.0% |
| other |  16.0% |  57.2% |
| jvm |  45.6% |   6.3% |
| user code anywhere on the stack |  25.7% | |

cpu self:   6.2% _raw_spin_unlock_irq_[k]
cpu self:   5.6% kotlinx/serialization/json/internal/JsonToStringWriter.writeQuoted
cpu self:   4.4% __syscall_cancel_arch_end
cpu self:   3.3% finish_task_switch.isra.0_[k]
cpu self:   2.3% do_syscall_64_[k]
## GC and JIT from the service log
lines=8
sat_rate=1818

# 2026-09-19T14:19:13Z label=pair-real-dblist warmup=45s measure=60s conns=64
# OpenJDK Runtime Environment (build 25.0.4+7-1-26.04-Ubuntu)
# model name	: Intel Xeon Processor (Skylake, IBRS, no TSX)
## dblist
clean: rps=1818 p50=9.56ms p99=98.07ms requests=100.0% ok
cost: cpu=128.8s over 60.0s = 2.15 cores busy, 1181 us cpu/req, 9.34 ctx/req, threads=101, peak rss=845 MiB, requests=109078
rps=1818 p50=3.10ms p99=18.56ms requests=100.0% ok
## /root/bench-results/pair-real-dblist/dblist.cpu.collapsed  (samples: 91563)
| category | self | owner |
|---|---|---|
| user |   0.5% |   0.7% |
| ktor |   3.4% |   5.5% |
| kotlinx |   9.6% |  16.0% |
| kotlin |   4.4% |   8.2% |
| jdk |  14.3% |   0.0% |
| other |  20.1% |  63.6% |
| jvm |  47.8% |   6.1% |
| user code anywhere on the stack |  54.0% | |

cpu self:   4.8% finish_task_switch.isra.0_[k]
cpu self:   3.7% _raw_spin_unlock_irqrestore_[k]
cpu self:   3.7% __syscall_cancel_arch_end
cpu self:   2.3% kotlinx/serialization/json/internal/JsonToStringWriter.writeQuoted
cpu self:   2.3% itable stub
## GC and JIT from the service log
lines=11
sat_rate=1818

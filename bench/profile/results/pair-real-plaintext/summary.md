# 2026-09-19T14:04:24Z label=pair-real-plaintext warmup=45s measure=60s conns=64
# OpenJDK Runtime Environment (build 25.0.4+7-1-26.04-Ubuntu)
# model name	: Intel Xeon Processor (Skylake, IBRS, no TSX)
## plaintext
clean: rps=34042 p50=1.03ms p99=6.73ms requests=100.0% ok
cost: cpu=166.8s over 60.0s = 2.78 cores busy, 82 us cpu/req, 0.60 ctx/req, threads=33, peak rss=809 MiB, requests=2042558
rps=34035 p50=1.18ms p99=6.38ms requests=100.0% ok
## /root/bench-results/pair-real-plaintext/plaintext.cpu.collapsed  (samples: 156319)
| category | self | owner |
|---|---|---|
| user |   0.0% |   0.0% |
| ktor |   7.6% |  12.7% |
| kotlinx |   4.6% |   6.1% |
| kotlin |   3.7% |   5.4% |
| jdk |  10.3% |   0.0% |
| other |  19.4% |  72.3% |
| jvm |  54.3% |   3.4% |
| user code anywhere on the stack |   7.0% | |

cpu self:   4.9% __syscall_cancel_arch_end
cpu self:   4.7% _raw_spin_unlock_irq_[k]
cpu self:   3.2% do_syscall_64_[k]
cpu self:   2.7% finish_task_switch.isra.0_[k]
cpu self:   2.6% iowrite16_[k]
## GC and JIT from the service log
lines=11
sat_rate=34043

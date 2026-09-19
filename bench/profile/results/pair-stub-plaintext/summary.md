# 2026-09-19T14:01:38Z label=pair-stub-plaintext warmup=45s measure=60s conns=64
# OpenJDK Runtime Environment (build 25.0.4+7-1-26.04-Ubuntu)
# model name	: Intel Xeon Processor (Skylake, IBRS, no TSX)
## plaintext
clean: rps=34042 p50=0.99ms p99=6.99ms requests=100.0% ok
cost: cpu=169.4s over 60.0s = 2.82 cores busy, 83 us cpu/req, 0.60 ctx/req, threads=31, peak rss=767 MiB, requests=2042572
rps=34042 p50=1.06ms p99=5.29ms requests=100.0% ok
## /root/bench-results/pair-stub-plaintext/plaintext.cpu.collapsed  (samples: 151461)
| category | self | owner |
|---|---|---|
| user |   0.0% |   0.0% |
| ktor |   7.7% |  12.6% |
| kotlinx |   5.0% |   6.4% |
| kotlin |   3.6% |   5.0% |
| jdk |   8.6% |   0.0% |
| other |  21.0% |  73.4% |
| jvm |  54.0% |   2.5% |
| user code anywhere on the stack |   7.0% | |

cpu self:   5.2% __syscall_cancel_arch_end
cpu self:   4.4% _raw_spin_unlock_irq_[k]
cpu self:   3.4% do_syscall_64_[k]
cpu self:   2.9% finish_task_switch.isra.0_[k]
cpu self:   2.8% iowrite16_[k]
## GC and JIT from the service log
lines=8
sat_rate=34043

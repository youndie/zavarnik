# 2026-09-19T13:01:51Z label=netty-jit warmup=45s measure=90s conns=64
# OpenJDK Runtime Environment (build 25.0.4+7-1-24.04-Ubuntu)
# model name	: Intel(R) Core(TM) Ultra 7 255HX
## business
clean: rps=91463 p50=0.65ms p99=1.56ms requests=100.0% ok
cost: cpu=586.7s over 90.0s = 6.52 cores busy, 71 us cpu/req, 0.63 ctx/req, threads=42, peak rss=848 MiB, requests=8231901
rps=89298 p50=0.67ms p99=1.58ms requests=100.0% ok
## /home/youndie/bench-results/netty-jit/business.cpu.collapsed  (samples: 134870)
| category | self | owner |
|---|---|---|
| user |   2.0% |   6.0% |
| ktor |   9.0% |  13.4% |
| kotlinx |  14.9% |  20.0% |
| kotlin |   8.5% |  16.8% |
| jdk |  19.0% |   0.0% |
| slf4j |   0.0% |   0.0% |
| other |  14.2% |  43.6% |
| jvm |  32.4% |   0.2% |
| user code anywhere on the stack |  40.6% | |

cpu self:   7.1% writev
cpu self:   6.1% epoll_wait
cpu self:   5.7% __GI___libc_write
cpu self:   3.6% __GI___read
cpu self:   2.4% itable stub
## GC and JIT from the service log
lines=8

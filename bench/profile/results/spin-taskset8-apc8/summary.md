# 2026-09-06T22:10:55Z label=spin-taskset8-apc8 warmup=30s measure=60s conns=64
# OpenJDK Runtime Environment (build 25.0.4+7-1-24.04-Ubuntu)
# model name	: Intel(R) Core(TM) Ultra 7 255HX
## business
clean: rps=32391 p50=1.23ms p99=10.78ms requests=100.0% ok
rps=27549 p50=1.52ms p99=11.63ms requests=100.0% ok
## /home/youndie/bench-results/spin-taskset8-apc8/business.cpu.collapsed  (samples: 171610)
| category | self | owner |
|---|---|---|
| user |   0.8% |   2.0% |
| ktor |   7.3% |  15.4% |
| kotlinx |  50.0% |  74.5% |
| kotlin |   4.7% |   8.0% |
| slf4j |   0.0% |   0.0% |
| jdk |   6.3% |   0.0% |
| jvm |  30.9% |   0.1% |
| user code anywhere on the stack |  18.7% | |

cpu self:  31.0% kotlinx/coroutines/internal/LockFreeTaskQueueCore.removeFirstOrNull
cpu self:  11.3% /usr/lib/x86_64-linux-gnu/libc.so.6
cpu self:   4.9% pthread_cond_signal
cpu self:   4.6% kotlinx/coroutines/scheduling/WorkQueue.pollBuffer
cpu self:   2.9% __write
## GC and JIT from the service log
lines=3

# 2026-09-11T00:37:20Z label=engine-jetty-c8 warmup=60s measure=120s conns=8
# OpenJDK Runtime Environment (build 25.0.4+7-1-24.04-Ubuntu)
# model name	: Intel(R) Core(TM) Ultra 7 255HX
## business
clean: rps=18105 p50=0.43ms p99=0.75ms requests=100.0% ok
cost: cpu=536.8s over 120.0s = 4.47 cores busy, 247 us cpu/req, 13.43 ctx/req, threads=70, peak rss=799 MiB, requests=2172660
rps=18086 p50=0.43ms p99=0.75ms requests=100.0% ok
## /home/youndie/bench-results/engine-jetty-c8/business.cpu.collapsed  (samples: 118443)
| category | self | owner |
|---|---|---|
| coroutines |   5.9% |  26.7% |
| serialization |   2.1% |   3.8% |
| jetty |   4.8% |  24.1% |
| user |   1.5% |   3.3% |
| ktor |   5.8% |   8.0% |
| kotlinx |   0.4% |   0.5% |
| kotlin |   5.8% |   9.5% |
| jdk |  16.4% |   4.8% |
| slf4j |   0.0% |   0.0% |
| jvm |  57.2% |  19.2% |
| user code anywhere on the stack |  29.0% | |

cpu self:  20.3% pthread_cond_signal
cpu self:  15.7% /usr/lib/x86_64-linux-gnu/libc.so.6
cpu self:   3.5% writev
cpu self:   2.0% java/util/concurrent/SynchronousQueue.xfer
cpu self:   2.0% itable stub
cpu self:   1.6% read
cpu self:   1.5% epoll_wait
cpu self:   1.4% java/util/concurrent/locks/LockSupport.unpark
cpu self:   1.3% java/util/HashMap.getNode
cpu self:   1.2% __write
cpu self:   1.1% epoll_ctl
cpu self:   0.8% pthread_cond_timedwait
cpu self:   0.8% org/eclipse/jetty/util/ArrayTrie.getBest
cpu self:   0.8% kotlin/coroutines/jvm/internal/ContinuationImpl.getContext
cpu self:   0.8% kotlin/coroutines/jvm/internal/BaseContinuationImpl.resumeWith
cpu self:   0.7% java/util/concurrent/ThreadPoolExecutor.runWorker
cpu self:   0.7% __vdso_clock_gettime
cpu self:   0.7% Parker::park
cpu self:   0.7% I2C/C2I adapters
cpu self:   0.6% AccessInternal::PostRuntimeDispatch<G1BarrierSet::AccessBarrier<286822ul, G1BarrierSet>, (AccessInternal::BarrierType)3, 286822ul>::oop_access_barrier
## GC and JIT from the service log
lines=6

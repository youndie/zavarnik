# 2026-09-06T22:28:32Z konekt screens rate=200 warmup=60s measure=120s container=konekt-server-1
# OpenJDK Runtime Environment Temurin-25.0.4+7 (build 25.0.4+7-LTS)
# image=ghcr.io/youndie/konekt-server:v0.1.40 cpus=1000000000 mem=1073741824
# java pid in container: 1
   ✓ checks.........................: 100.00% 66031 out of 66031
     http_req_duration..............: avg=1.67ms  min=426.14µs med=1.12ms  max=1.93s    p(90)=2.71ms  p(95)=2.93ms 
     http_reqs......................: 66181   188.799079/s
## /home/youndie/bench-results/konekt-screens-200/screens.cpu.collapsed  (samples: 6140)
| category | self | owner |
|---|---|---|
| user |   0.5% |   0.9% |
| kompot |   0.2% |   0.2% |
| exposed |   2.8% |   6.9% |
| postgres |   2.0% |  22.0% |
| ktor |   4.6% |  13.6% |
| kotlinx |   9.4% |  37.5% |
| kotlin |   3.9% |   8.9% |
| jdk |  17.1% |   0.1% |
| other |   1.4% |   4.0% |
| jvm |  58.1% |   5.9% |
| user code anywhere on the stack |  42.1% | |

## /home/youndie/bench-results/konekt-screens-200/screens.alloc.collapsed  (samples: 2092429417)
| category | self | owner |
|---|---|---|
| user |   1.3% |   5.4% |
| kompot |   1.1% |   1.1% |
| exposed |   3.6% |  14.5% |
| postgres |   2.0% |   4.5% |
| ktor |  12.4% |  23.3% |
| kotlinx |   4.5% |  12.9% |
| kotlin |   5.7% |  25.3% |
| jdk |  28.4% |   0.0% |
| other |   4.6% |  13.0% |
| jvm |  36.4% |   0.0% |
| user code anywhere on the stack |  54.6% | |

cpu self:  29.2% /usr/lib/x86_64-linux-gnu/libc.so.6
cpu self:  10.3% pthread_cond_signal
cpu self:   3.0% itable stub
cpu self:   1.2% kotlinx/coroutines/scheduling/CoroutineScheduler$Worker.executeTask
cpu self:   1.0% java/util/concurrent/atomic/AtomicIntegerFieldUpdater$AtomicIntegerFieldUpdaterImpl.get
cpu self:   0.8% kotlinx/serialization/json/internal/JsonToStringWriter.writeQuoted
cpu self:   0.8% epoll_ctl
cpu self:   0.7% jbyte_disjoint_arraycopy

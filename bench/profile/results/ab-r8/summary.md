# 2026-09-06T21:19:22Z label=ab-r8 reps=3 warmup=30s measure=60s conns=64
# A: /home/youndie/zavarnik/bench/build/install/bench/bin/bench
# B: /usr/lib/jvm/java-25-openjdk-amd64/bin/java -Dbench.port=18100 -XX:+UnlockDiagnosticVMOptions -XX:-AOTAdapterCaching -cp /home/youndie/bench-results/r8-jar-user/bench-r8-user.jar:/home/youndie/zavarnik/bench/build/install/bench/lib/annotations-23.0.0.jar:/home/youndie/zavarnik/bench/build/install/bench/lib/config-1.4.9.jar:/home/youndie/zavarnik/bench/build/install/bench/lib/kotlin-reflect-2.3.21.jar:/home/youndie/zavarnik/bench/build/install/bench/lib/kotlin-stdlib-2.4.10.jar:/home/youndie/zavarnik/bench/build/install/bench/lib/kotlinx-coroutines-core-jvm-1.11.0.jar:/home/youndie/zavarnik/bench/build/install/bench/lib/kotlinx-io-bytestring-jvm-0.9.1.jar:/home/youndie/zavarnik/bench/build/install/bench/lib/kotlinx-io-core-jvm-0.9.1.jar:/home/youndie/zavarnik/bench/build/install/bench/lib/kotlinx-serialization-core-jvm-1.11.0.jar:/home/youndie/zavarnik/bench/build/install/bench/lib/kotlinx-serialization-json-io-jvm-1.11.0.jar:/home/youndie/zavarnik/bench/build/install/bench/lib/kotlinx-serialization-json-jvm-1.11.0.jar:/home/youndie/zavarnik/bench/build/install/bench/lib/ktor-events-jvm-3.5.2.jar:/home/youndie/zavarnik/bench/build/install/bench/lib/ktor-http-cio-jvm-3.5.2.jar:/home/youndie/zavarnik/bench/build/install/bench/lib/ktor-http-jvm-3.5.2.jar:/home/youndie/zavarnik/bench/build/install/bench/lib/ktor-io-jvm-3.5.2.jar:/home/youndie/zavarnik/bench/build/install/bench/lib/ktor-network-jvm-3.5.2.jar:/home/youndie/zavarnik/bench/build/install/bench/lib/ktor-serialization-jvm-3.5.2.jar:/home/youndie/zavarnik/bench/build/install/bench/lib/ktor-serialization-kotlinx-json-jvm-3.5.2.jar:/home/youndie/zavarnik/bench/build/install/bench/lib/ktor-serialization-kotlinx-jvm-3.5.2.jar:/home/youndie/zavarnik/bench/build/install/bench/lib/ktor-server-cio-jvm-3.5.2.jar:/home/youndie/zavarnik/bench/build/install/bench/lib/ktor-server-content-negotiation-jvm-3.5.2.jar:/home/youndie/zavarnik/bench/build/install/bench/lib/ktor-server-core-jvm-3.5.2.jar:/home/youndie/zavarnik/bench/build/install/bench/lib/ktor-utils-jvm-3.5.2.jar:/home/youndie/zavarnik/bench/build/install/bench/lib/ktor-websockets-jvm-3.5.2.jar:/home/youndie/zavarnik/bench/build/install/bench/lib/slf4j-api-2.0.18.jar:/home/youndie/zavarnik/bench/build/install/bench/lib/slf4j-simple-2.0.17.jar bench.MainKt
A rep1 echo: rps=36984 p50=1.00ms p99=13.39ms ok=100.0%
A rep1 items: rps=40063 p50=1.19ms p99=8.20ms ok=100.0%
A rep1 business: rps=23887 p50=1.46ms p99=17.51ms ok=100.0%
B rep1 echo: rps=37934 p50=0.98ms p99=13.72ms ok=100.0%
B rep1 items: rps=28808 p50=1.32ms p99=16.53ms ok=100.0%
B rep1 business: rps=24265 p50=1.40ms p99=17.76ms ok=100.0%
A rep2 echo: rps=44292 p50=0.92ms p99=10.64ms ok=100.0%
A rep2 items: rps=37439 p50=1.14ms p99=11.64ms ok=100.0%
A rep2 business: rps=27531 p50=1.29ms p99=16.12ms ok=100.0%
B rep2 echo: rps=41434 p50=0.96ms p99=11.41ms ok=100.0%
B rep2 items: rps=37143 p50=1.15ms p99=11.60ms ok=100.0%
B rep2 business: rps=28241 p50=1.30ms p99=14.71ms ok=100.0%
A rep3 echo: rps=36990 p50=1.02ms p99=13.02ms ok=100.0%
A rep3 items: rps=36450 p50=1.16ms p99=11.65ms ok=100.0%
A rep3 business: rps=28535 p50=1.27ms p99=14.50ms ok=100.0%
B rep3 echo: rps=44492 p50=0.93ms p99=10.05ms ok=100.0%
B rep3 items: rps=39023 p50=1.11ms p99=10.83ms ok=100.0%
B rep3 business: rps=30176 p50=1.22ms p99=13.84ms ok=100.0%
## medians
| endpoint | A rps median | B rps median | B/A | A p99 | B p99 |
|---|---|---|---|---|---|
| echo | 36990 (36984 36990 44292) | 41434 (37934 41434 44492) | 1.120 | 13.02 ms | 11.41 ms |
| items | 37439 (36450 37439 40063) | 37143 (28808 37143 39023) | 0.992 | 11.64 ms | 11.60 ms |
| business | 27531 (23887 27531 28535) | 28241 (24265 28241 30176) | 1.026 | 16.12 ms | 14.71 ms |

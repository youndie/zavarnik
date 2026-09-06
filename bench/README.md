# bench

The benchmark service of the optimizer research (`docs/research/research-optimizer.md`): a Ktor
CIO server with the three kinds of endpoint the brief names — `/echo`, JSON CRUD over an
in-memory store under `/items`, and `/business`, a quote calculation written the way services are
written, with every construct the research treats as a candidate in its natural place.

```bash
./gradlew -p bench installDist                       # the service, on the zavarnik plugin
JAVA_HOME=… bench/profile/run.sh                     # RQ0: load + CPU/alloc profiles per endpoint
JAVA_HOME=… bench/profile/r8.sh                      # the R8 baseline jar (see the research: it does not start)
START="java -cp <r8 jar>:<libs> bench.MainKt" LABEL=r8 bench/profile/run.sh
```

`profile/run.sh` pins the JVM and the load generator (`oha`) to disjoint cores, warms up, then
measures rps and latency while async-profiler samples CPU and then allocations; `attribute.py`
splits every sample by where the code came from. Results are written outside the tree (a
one-way replica would delete them mid-run) and copied into `profile/results/<label>/` from the
machine that owns the tree. The committed results are the ones the research cites.

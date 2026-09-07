# samples/ktor

A Ktor server on the `application` plugin with zavarnik applied. From the repository root:

```bash
./gradlew -p samples/ktor check      # aotTrain → aotVerify, on check
./gradlew -p samples/ktor distTar    # the distribution with lib/app.aot inside
./gradlew -p samples/ktor aotReport  # cold vs cached: readiness, and what the JIT still compiles after the start
samples/ktor/docker-check.sh         # the image, verified by the image's own JVM
```

`Dockerfile` is the container recipe: the `-jdk` build stage only installs the distribution; the
`-jre` runtime stage trains and verifies the cache with `lib/zavarnik-runner.jar` on its own JVM,
because the JVM accepts a cache only from the JDK build that made it — and the `-jre` package is
a different build to it. `docker-check.sh` builds the image, starts it with the cache made
mandatory and counts, from the container's own output, how many classes came from the cache.
Measured: 562 MB against 647 MB for two `-jdk` stages, every `sample.*` class from the cache
(`experiments/runner-jre/`).

`build.gradle.kts` is the whole integration: the plugin id, a port in `jvmArgs`, a readiness URL
and a workload of two `curl` calls. The application itself is the stand measured in
`experiments/ktor-readiness`, unchanged but for the package name.

`aotReport` shows two tables on purpose. Readiness improves by a factor of two to three; the JIT
work after the start does not — the cache carries classes, heap objects and method profiles,
not compiled code, so C2 compiles the same thousand-odd methods either way. A report that showed
only the first table would promise a warm service the JVM does not deliver.

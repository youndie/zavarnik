# samples/ktor

A Ktor server on the `application` plugin with zavarnik applied. From the repository root:

```bash
./gradlew -p samples/ktor check      # aotTrain → aotVerify, on check
./gradlew -p samples/ktor distTar    # the distribution with lib/app.aot inside
./gradlew -p samples/ktor aotReport  # cold vs cached readiness, build/reports/zavarnik/aotReport.md
samples/ktor/docker-check.sh         # the image, verified by the image's own JVM
```

`Dockerfile` is the container recipe: the cache is trained in the build stage on the same Temurin
build the runtime stage ships, because the JVM accepts a cache only from the JDK build that made
it. `docker-check.sh` builds the image, starts it with the cache made mandatory and counts, from
the container's own output, how many classes came from the cache.

`build.gradle.kts` is the whole integration: the plugin id, a port in `jvmArgs`, a readiness URL
and a workload of two `curl` calls. The application itself is the stand measured in
`experiments/ktor-readiness`, unchanged but for the package name.

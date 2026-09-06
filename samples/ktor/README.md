# samples/ktor

A Ktor server on the `application` plugin with zavarnik applied. From the repository root:

```bash
./gradlew -p samples/ktor check      # aotTrain → aotVerify, on check
./gradlew -p samples/ktor distTar    # the distribution with lib/app.aot inside
```

`build.gradle.kts` is the whole integration: the plugin id, a port in `jvmArgs`, a readiness URL
and a workload of two `curl` calls. The application itself is the stand measured in
`experiments/ktor-readiness`, unchanged but for the package name.

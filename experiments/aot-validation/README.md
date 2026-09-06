# aot-validation

The experiment behind §1 of [docs/research/research-architecture.md](../../docs/research/research-architecture.md):
what HotSpot actually validates before it uses an AOT cache, and what the training run has to do
for the cache to be written at all.

`run.sh` builds a one-class application, trains a cache with the one-step workflow
(`-XX:AOTCacheOutput`), and then runs it under every condition the plugin has to detect or
document — relocated directory, touched jar, replaced jar, extra classpath entries, agents, module
options, a different GC, `Runtime.halt`, `SIGTERM`, `SIGKILL`, a directory on the classpath. It
asserts nothing. Each case ends with a `shared=N file=M` line: `shared` is how many classes the
JVM reported as loaded from the cache, `file` whether the application class came from the jar
instead. The log is the result.

```bash
JAVA_HOME=/path/to/jdk-25 ./run.sh /tmp/aot-work
```

`results/` holds the logs the research cites, one per JDK build and machine, named
`<date>-<os>-<arch>-<jdk>.log`. Re-run on a new JDK and commit the log next to them; the
research document is amended from the diff, not rewritten.

Two of the three logs disagree on purpose. OpenJDK 25.0.2 accepts a cache whose jar has been
replaced ([JDK-8377932](https://bugs.openjdk.org/browse/JDK-8377932)); 25.0.4 rejects it. That
difference is the reason the plugin verifies the jars itself.

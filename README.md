# zavarnik

A Gradle plugin that gives a plain JVM application — Ktor, http4k, anything on the `application`
plugin — a [Project Leyden](https://openjdk.org/projects/leyden/) AOT cache: train it, **verify
that production will actually accept it**, and ship it inside the distribution. Spring Boot and
Quarkus have this built in; everything else has a two-command workflow that fails silently when
the environment differs. This plugin turns that silence into a red build.

**Status: research, no code yet.** What the JVM validates before it uses a cache, what a training
run has to do for the cache to be written at all, and what already exists elsewhere — all of it
is measured, not assumed, and recorded in [`docs/research/`](docs/research/). Three findings shape
the plugin:

- the cache survives relocation to a different absolute path, but not a touched jar, a prepended
  classpath entry, `--add-modules`, a `-javaagent`, or ZGC on JDK 25;
- OpenJDK 25.0.0–25.0.3 and 26.0.0–26.0.1 never validate the application jars against the cache
  ([JDK-8377932](https://bugs.openjdk.org/browse/JDK-8377932)) — a stale cache is used silently,
  so the plugin has to verify the jars itself;
- the cache is written on any exit except `SIGKILL`, including `Runtime.halt` and `SIGTERM`, so a
  training run needs no hook inside the application.

The experiment behind those statements is [`experiments/aot-validation/`](experiments/aot-validation/);
its logs are committed, and re-running the script on a new JDK is how the research gets amended.

Documentation lives in [`docs/`](docs/README.md) and is written for a coding agent first: every
claim carries a path to where it was verified. The plan is [`backlog.md`](backlog.md).

## Checks

```bash
pip install pyyaml
make check
```

## License

MIT — see [LICENSE](LICENSE).

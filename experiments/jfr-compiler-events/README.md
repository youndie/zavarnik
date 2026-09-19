# jfr-compiler-events

Does JFR report what the compiler did, on the settings it ships with?

The fifth phase's brief ([source-brief-jit-constructs](../../docs/research/source-brief-jit-constructs.md))
starts measuring "after JFR shows no C2 compilations for 60 seconds", and puts JFR down for
"compiler behaviour on the live service without diagnostic flags". Both are claims about an
instrument, so they are checked against a second one: the same run also prints every compilation
under `-XX:+PrintCompilation`, and that count is what JFR is held against.

`run.sh` runs one hot-loop program three times:

- **shipped** — `settings=profile`, exactly as the JDK ships it;
- **forced** — the same, with `jdk.Compilation#threshold=0ms` and `+jdk.CompilerInlining#enabled=true`;
- **bare** — `settings=none` with every event enabled explicitly, so that no `.jfc` control can
  override the command line.

```bash
JAVA_HOME=/path/to/jdk25 ./run.sh
```

Each run writes `results/<stamp>.log` — the counts, with the relevant part of `profile.jfc` quoted
at the head — and `results/<stamp>.<arm>.printcompilation.log`, the raw output of the second
instrument, so the counts can be recomputed without the JDK that produced them. The recordings
themselves are not committed: the script regenerates them, and what the research quotes is the
count.

The answer is in [research-jit-constructs](../../docs/research/research-jit-constructs.md) §1.3.
In short: on the shipped settings the answer is zero whatever the JVM is doing, which means a gate
phrased as "no compilations for 60 seconds" cannot fail. Forced on, roughly 60 of 1340 compilations
arrive, inside a window of some twenty milliseconds — the count repeats between runs and the window
does not, so neither is a census. Why is open question 1 of that document.

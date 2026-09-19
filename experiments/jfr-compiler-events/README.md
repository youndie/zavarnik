# jfr-compiler-events

Does JFR report what the compiler did, on the settings it ships with?

The fifth phase's brief ([source-brief-jit-constructs](../../docs/research/source-brief-jit-constructs.md))
starts measuring "after JFR shows no C2 compilations for 60 seconds", and puts JFR down for
"compiler behaviour on the live service without diagnostic flags". Both are claims about an
instrument, so they are checked against a second one: every run here also prints each compilation
under `-XX:+PrintCompilation`, and that count is what JFR is held against.

Two scripts, and the second exists because the first one's answer was wrong.

**`run.sh`** — a single hot loop, three recording arms:

- **shipped** — `settings=profile`, exactly as the JDK ships it;
- **forced** — the same, with `jdk.Compilation#threshold=0ms` and `+jdk.CompilerInlining#enabled=true`;
- **bare** — `settings=none` with every event enabled explicitly, so that no `.jfc` control can
  override the command line.

**`long-run.sh`** — the control. 3000 methods made hot one after another, so compilation runs for
the whole program instead of finishing in its first second; the same arms, plus the price of each
instrument over three interleaved repeats.

```bash
JAVA_HOME=/path/to/jdk25 ./run.sh
JAVA_HOME=/path/to/jdk25 ./long-run.sh
```

Each run writes `results/<stamp>.log` (or `.long.log`) — the counts, with the relevant part of
`profile.jfc` quoted at the head — and `results/<stamp>.<arm>.printcompilation.log`, the raw output
of the second instrument, so the counts can be recomputed without the JDK that produced them. The
recordings themselves are not committed: the scripts regenerate them, and what the research quotes
is the count.

The answer is in [research-jit-constructs](../../docs/research/research-jit-constructs.md) §1.3:

- on the settings it ships with, JFR reports **zero** compilations whatever the JVM is doing — 7262
  compile tasks in 7.3 seconds, 0 `jdk.Compilation` events — so a gate phrased as "no compilations
  for 60 seconds" cannot fail;
- switched on explicitly, `jdk.Compilation` is a **census**: 6025 events against 6058 compile tasks
  in its id range, missing only what compiled before the recording was live;
- `jdk.CompilerInlining` is the one that truncates — a few dozen compilations at the start of the
  recording, reproducibly, every run;
- watching is not free, and the ranking is the opposite of the brief's assumption: JFR +4.8 %,
  `-XX:+PrintCompilation` +1.6 % on this workload.

`run.sh` alone said JFR "is not a census". That was the subject, not the instrument: its one-method
loop stops compiling before the recording is live. The short arms are kept because the comparison
between them and the long ones is the most useful thing either produced.

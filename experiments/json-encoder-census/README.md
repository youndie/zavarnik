# json-encoder-census

How many `Encoder` implementations does a JSON-only service actually load?

RQ6 of the fifth phase asks whether the `Encoder` and `Decoder` calls inside a generated serialiser
stay monomorphic and inline. `kotlinx-serialization-json` ships six encoder implementations, so the
question is not how many exist but how many the process loads — and that is a runtime fact.

`Probe.kt` is shaped like the brief's list endpoint: encode a page of fifty rows, decode a body
back, two thousand times each. `run.sh` compiles it with **the Kotlin version the stand pins**, from
the Gradle cache, so the generated serialiser is the one the service would run — no Gradle build and
no daemon — and runs it twice under `-verbose:class`:

- **string** — `encodeToString` and `decodeFromString` only;
- **element** — the same, plus **one** `encodeToJsonElement` call.

```bash
JAVA_HOME=/path/to/jdk25 ./run.sh
```

The answer is in [research-jit-constructs](../../docs/research/research-jit-constructs.md) §1.5:
the string arm loads exactly one concrete encoder and one concrete decoder; the single tree call
adds three more concrete encoders, 15 loaded classes becoming 23. `TypeProfileWidth` is 2, so that
one line is the whole distance between a site C2 can inline and a megamorphic one. Ktor's converter
does not cross the line itself — zero classes in either of its kotlinx artifacts reference
`JsonElement`.

What this does **not** answer is the price, which needs the receiver counts the compilation log
shows under load: [B-46](../../docs/backlog/B-46-rq6-encoder-receiver-census.md).

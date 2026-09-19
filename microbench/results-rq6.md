# 2026-09-19T20:00:52Z label=rq6 forks=3 warmup=5x2s measure=5x2s
# host: 4 cores,: 0.35, 0.18, 0.27, steal 0
# openjdk version "25.0.4" 2026-07-21

Benchmark                                  Mode  Cnt     Score    Error   Units
EncoderClean.encode                        avgt   15    25.427 ±  0.972   us/op
EncoderClean.encode:gc.alloc.rate.norm     avgt   15  9888.090 ±  0.003    B/op
EncoderPolluted.encode                     avgt   15    25.386 ±  1.035   us/op
EncoderPolluted.encode:gc.alloc.rate.norm  avgt   15  9888.089 ±  0.004    B/op
Benchmark result is saved to /root/jmh-rq6.json

# 2026-09-19T20:03:33Z label=rq6b forks=3 warmup=5x2s measure=5x2s
# host: 4 cores,: 0.61, 0.49, 0.39, steal 0
# openjdk version "25.0.4" 2026-07-21

Benchmark                                     Mode  Cnt      Score    Error   Units
EncoderDoubleClean.encode                     avgt   15     49.652 ±  2.589   us/op
EncoderDoubleClean.encode:gc.alloc.rate.norm  avgt   15  19776.175 ±  0.009    B/op
EncoderMixed.encode                           avgt   15     58.165 ±  1.652   us/op
EncoderMixed.encode:gc.alloc.rate.norm        avgt   15  67624.205 ±  0.006    B/op
Benchmark result is saved to /root/jmh-rq6b.json
# inlining evidence, RQ6 — type profiles at the shared Encoder call sites

## EncoderClean
      5 TypeProfile (36102/36102 counts) = kotlinx/serialization/json/internal/JsonToStringWriter
      4 TypeProfile (11840/11840 counts) = kotlinx/serialization/json/internal/JsonToStringWriter
      3 TypeProfile (42899/42899 counts) = kotlinx/serialization/json/internal/JsonToStringWriter
      3 TypeProfile (3397/6727 counts) = kotlinx/serialization/internal/ArrayListClassDesc
      3 TypeProfile (3330/6727 counts) = kotlinx/serialization/internal/PluginGeneratedSerialDescriptor
      3 TypeProfile (10618/10618 counts) = kotlinx/serialization/json/internal/StreamingJsonEncoder
      3 TypeProfile (10618/10618 counts) = kotlinx/serialization/json/internal/JsonToStringWriter
      2 TypeProfile (8321/8321 counts) = kotlinx/serialization/json/internal/StreamingJsonEncoder

## EncoderMixed
     12 TypeProfile (4989/9962 counts) = kotlinx/serialization/json/internal/StreamingJsonEncoder
     12 TypeProfile (4973/9962 counts) = kotlinx/serialization/json/internal/JsonTreeEncoder
     10 TypeProfile (6402/6402 counts) = kotlinx/serialization/internal/PluginGeneratedSerialDescriptor
      6 TypeProfile (5681/11361 counts) = kotlinx/serialization/json/internal/JsonTreeListEncoder
      6 TypeProfile (5680/11361 counts) = kotlinx/serialization/json/internal/JsonTreeEncoder
      5 TypeProfile (9890/9890 counts) = kotlinx/serialization/json/internal/JsonToStringWriter
      5 TypeProfile (7644/7720 counts) = kotlinx/serialization/json/internal/AbstractJsonTreeEncoder
      5 TypeProfile (76/7720 counts) = kotlinx/serialization/json/internal/TreeJsonEncoderKt

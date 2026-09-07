---
id: B-31
title: "Wildcard в CLASSPATH стартового скрипта отказывается: порядок раскрытия разный у разных рантаймов"
status: done
priority: P0
size: S
stage: stage-3-packaging
---

# B-31 — `lib/*` в стартовом скрипте — отказ

Первая раскатка кэша в кластер konekt (v0.1.41, 08.09.2026): JVM отвергла кэш —
«The name of app classpath [1] does not match: expected '/opt/konekt/lib/ktor-http-cio-jvm-3.5.2.jar',
got '/opt/konekt/lib/packages-shared-api-jvm-0.1.0-SNAPSHOT.jar'» — и стартовала без него, молча.
Кэш тренировался в CI под Docker overlay2, а узел k0s с containerd раскрыл `lib/*` в другом
порядке. B-27 утверждала, что wildcard безопасен, потому что порядок «стабилен в одном каталоге»
(W1–W4) — проверка была на одной файловой системе и ничего не говорила про другую.
`aotVerify` этого поймать не мог: он проверяет там, где тренировали.

- **Решение:** `StartScriptGuard` отказывает, если в `CLASSPATH` стартового скрипта есть `/*`
  или `\*`, с объяснением; правильный путь — перечислить jar-ы (у konekt — переименованные
  имена из его же карты коллизий).
- Ресёрч §4: запись о раскатке как отрицательный результат с адресом.
- AC: `startScripts { classpath = files("lib/*") }` → `installDist` падает с текстом про порядок
  раскрытия. **Automated:** `DistributionFunctionalTest`, `StartScriptGuardTest`.
- Якоря: `zavarnik-gradle-plugin/src/main/kotlin/io/github/youndie/zavarnik/StartScriptGuard.kt`.

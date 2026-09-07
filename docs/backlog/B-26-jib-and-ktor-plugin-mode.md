---
id: B-26
title: "Режим для Jib и Ktor-плагина: тренировка в образе без стартового скрипта, кэш вторым слоем"
status: open
priority: P2
size: M
stage: stage-3-packaging
---

# B-26 — Jib и `ktor { docker { } }`

Ktor-плагин собирает образ через Jib (`jibDockerBuild`/`jibBuildTar`/`jib`, база
`eclipse-temurin:<jreVersion>-jre`, `customBaseImage`); своего DSL для Jib у него нет, расширение
`jib { }` доступно. Проверено по `Docker.kt` плагина и README/FAQ Jib, 07.09.2026. Что это
значит для кэша:

| Факт (Jib) | Следствие |
|---|---|
| Раскладка по умолчанию `exploded`: classpath `app/libs/*:app/resources:app/classes` | Два каталога на classpath — JVM не пишет кэш (E4: «Cannot have non-empty directory in paths»). `ktor { docker { } }` как есть даёт ноль и молчит об этом |
| `jib.containerizingMode = "packaged"` — jar проекта как jar | Одни jar-ы, кэш возможен; `lib/*` проверено 07.09.2026 (W1–W4) |
| Всем файлам `filesModificationTime = EPOCH_PLUS_SECOND`, для `extraDirectories` отдельного нет | Наша константа D3 (`1970-01-02`) не совпадает: раннер в этом режиме mtime **не трогает**, тренируется на том, что есть |
| Jib не запускает контейнеров и не требует Docker | Тренировать «внутри образа» некуда. Рецепт: `jibDockerBuild` → `docker run` образа с раннером → `docker cp` кэша → второй Jib-билд с кэшем в `extraDirectories` и `jvmFlags += -XX:AOTCache=…`. Второй образ отличается одним слоем, jar-ы и их mtime те же |
| В образе нет стартового скрипта: entrypoint `java -cp … Main` | Раннеру нужен режим «java + classpath + main + флаги», записанный плагином в `zavarnik.properties` из `jib { }` |

- Раннер: режим без стартового скрипта; опция не нормализовать mtime — S.
- Плагин: при применённом Jib — `jibAotTrain`/`jibAotVerify` (docker run образа с раннером,
  извлечение кэша, второй билд с `extraDirectories`); **отказ на конфигурации**, если
  `containerizingMode` не `packaged`, с текстом, почему кэша не будет — M.
- Образец на `io.ktor.plugin` рядом с `samples/ktor`, TestKit-тест с Docker — M.
- README: режим требует Docker на машине сборки — ровно то, чего Jib позволял не иметь; `jib` в
  реестр без демона кэша не даст. Написать прямо.
- Отвергнуто: `extraDirectories` со всем `installDist` и `entrypoint` на стартовый скрипт —
  это `COPY` без слоёв Jib, теряется всё, ради чего берут Jib.

- AC: `ktor { docker { } }` + `jib { containerizingMode = "packaged" }` + zavarnik → образ, который
  под `-XX:AOTMode=on` стартует с ≥ 90 % классов приложения из кэша; `docker-check`-подобный
  скрипт зелёный.
- AC: с `exploded` сборка падает на конфигурации с сообщением про каталоги на classpath.
- Якоря: `zavarnik-runner/src/main/kotlin/io/github/youndie/zavarnik/runner/Installation.kt`,
  `zavarnik-runner/src/main/kotlin/io/github/youndie/zavarnik/runner/Training.kt`,
  `zavarnik-gradle-plugin/src/main/kotlin/io/github/youndie/zavarnik/ZavarnikPlugin.kt`,
  `docs/research/research-architecture.md` (§1.5, D3), `docs/backlog/B-10-docker-and-jib-recipe.md`.
- После: [B-27](B-27-workload-headers-and-captures.md) (заголовки и захват — нужны и здесь).

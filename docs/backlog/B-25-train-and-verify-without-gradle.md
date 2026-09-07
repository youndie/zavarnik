---
id: B-25
title: "Тренировка и проверка без Gradle: раннер в lib/, чтобы JRE-стадия образа тренировала кэш сама"
status: open
priority: P1
size: M
stage: stage-3-packaging
---

# B-25 — Раннер: `train` и `verify` без Gradle, внутри рантайм-образа

Issue youndie/zavarnik#2: JRE-образ пишет и читает свой кэш, а рецепт образца ставит обе стадии
на `-jdk` (647 МБ) только потому, что тренировку делает Gradle. Если тренировать в JRE-стадии
запуском установленного дистрибутива, образ остаётся JRE, а кэш — из того же образа, что и
запуск. Чего нет в JRE-образе: Gradle, `curl` (§1.5 второй фазы — Temurin без него), значит
готовность, нагрузка, SIGTERM, манифест и три проверки `aotVerify` должны уметь работать из
одного jar-а на голой `java`.

- **Решение: небольшой `zavarnik-runner.jar`, который плагин кладёт в `lib/` дистрибутива**
  (`java -cp lib/zavarnik-runner.jar train|verify`), с той же логикой, что у задач:
  `StartScriptRun`, `Workload` (HTTP через `HttpClient`, без curl), `JarManifest`,
  `LoadedClasses`, `JitStats` — они уже не зависят от Gradle API, только от `GradleException`,
  которую надо заменить своим исключением. Задачи `aotTrain`/`aotVerify` становятся обёртками
  над раннером; конфигурация (`readyWhen`, `workload`, пороги) уезжает в файл рядом с кэшем
  (`lib/zavarnik.json`), который пишет плагин.
- Рецепт образца после этого: `-jdk` собирает `installDist`, `-jre` копирует, запускает
  `runner train`, затем `runner verify` в том же слое, и это его окончательный образ.
- Отвергнуто: тренировать в JRE-стадии «по таймеру» (`timeout -s TERM`), без готовности и
  нагрузки — кэш без профилей горячих запросов, и без манифеста; проверено как эксперимент
  (§1.5, правка), в продукт не идёт.
- Не покрывает: Gradle на JRE — не нужно.

- AC: `docker build` образца с `-jre` в стадии рантайма даёт образ, который под
  `-XX:AOTMode=on` стартует с 100 % классов `sample.*` из кэша; `docker-check.sh` зелёный;
  размер образа записан рядом с 647 МБ.
- AC: `runner verify` внутри контейнера падает на подменённом jar с тем же текстом, что `aotVerify`.
- Якоря: `zavarnik-gradle-plugin/src/main/kotlin/io/github/youndie/zavarnik/StartScriptRun.kt`,
  `zavarnik-gradle-plugin/src/main/kotlin/io/github/youndie/zavarnik/Workload.kt`,
  `zavarnik-gradle-plugin/src/main/kotlin/io/github/youndie/zavarnik/AotVerifyTask.kt`,
  `samples/ktor/Dockerfile`, `docs/research/research-architecture.md` (§1.1 следствие 5).

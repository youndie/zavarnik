---
id: B-08
title: "TestKit: каждый случай инвалидации из журнала эксперимента становится тестом плагина"
status: done
priority: P1
size: M
stage: stage-2-mvp
blocked_by: [B-06]
---

# B-08 — TestKit на каждое условие инвалидации

> **Сделано 06.09.2026.** `InvalidationFunctionalTest`: R3 (тронутый jar — на JDK с проверкой
> «timestamp has changed», на 25.0.2 проходит, и тест это знает через `JdkVersion`), R8
> (`verify { jvmArgs("--add-modules", …) }` → «Mismatched values for property
> jdk.module.addmods»), R9 (агент только в `verify` — отказ по `java.instrument`; агент в
> `zavarnik.jvmArgs` — принят), E4 — **не воспроизводится**: каталог в `runtimeOnly(files("conf"))` `installDist` раскладывает
> плоско в `lib/`, в CLASSPATH остаётся висячая запись, JVM её терпит, кэш тренируется; тест
> фиксирует это как квирк Gradle (ресёрч §1.4). R4 — в `AotVerifyFunctionalTest`, R11 — в `ConfigurationChecksFunctionalTest`.
> R6 (jar впереди classpath) не воспроизводится через плагин: порядок classpath задаёт Gradle,
> и его смена — это новый `installDist`, за которым идёт новая тренировка. Сверх плана: DSL
> `verify { jvmArgs }` — то, что прод добавляет сверх скрипта. Матрица с тулчейном 25.0.2 не
> заведена: на CI один JDK 25, ветвление по версии — внутри теста.

Журнал `experiments/aot-validation/results/` — это список того, что `aotVerify` обязан ловить,
и он уже написан. Бриф требовал «TestKit на каждое условие из RQ1–RQ3»; условия теперь
называются буквами: R3 (mtime), R4 (подмена jar), R6 (jar впереди), R8 (`--add-modules`), R9
(`-javaagent`), R11 (ZGC на 25), E4 (каталог на classpath).

- **Решение: один параметризованный TestKit-тест на случай, входные данные — те же
  манипуляции, что в `run.sh`**, чтобы тест и эксперимент расходились только в обвязке. Тест на
  JDK, у которого нет собственной проверки (25.0.2), — отдельный тулчейн в матрице, чтобы
  проверка (3) из [B-06](B-06-aot-verify-task.md) не проходила «по другой причине».
- Отвергнуто: юнит-тесты на парсер журнала. Они проверяют мой ответ, а не то, что уцелеет при
  смене формата строк.
- Не покрывает: переносимость между CPU — её в TestKit не воспроизвести
  ([B-09](B-09-cpu-portability-adapter-caching.md)).

- AC: `./gradlew :zavarnik-gradle-plugin:test` гоняет семь случаев на тулчейне 25.0.4 и три (R3,
  R4, R6) на 25.0.2; каждый красный `aotVerify` называет ту же причину, что журнал.
- Якоря: `zavarnik-gradle-plugin/src/functionalTest/kotlin/io/github/youndie/zavarnik/InvalidationFunctionalTest.kt`,
  `zavarnik-gradle-plugin/src/functionalTest/kotlin/io/github/youndie/zavarnik/Fixture.kt`,
  `experiments/aot-validation/run.sh`, `experiments/aot-validation/results/`.

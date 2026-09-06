---
id: B-04
title: "Каркас плагина: расширение zavarnik, проверки тулчейна до первой задачи"
status: done
priority: P0
size: M
stage: stage-2-mvp
blocked_by: [B-01]
---

# B-04 — Каркас плагина: расширение `zavarnik`, проверки тулчейна

> **Сделано 06.09.2026.** Модуль `zavarnik-gradle-plugin` на sborka 0.3.0.31 (`io.github.youndie.sborka.*`),
> Kotlin 2.4.10, тулчейн 25, пол 17. Расширение `zavarnik { jvmArgs; portability; cacheFileName;
> training { readyWhen.url; workload { exec }; exitAfter; readyTimeout; shutdownTimeout }; verify {
> minCachedShare; onCheck } }`. `ConfigurationChecks` в `afterEvaluate`: нет `application` — ошибка;
> тулчейн < 25 — ошибка; `-XX:+UseZGC` при < 26 — ошибка с JEP 516; JDK с JDK-8377932 — предупреждение.
> `JdkVersion` знает границу 25.0.4 / 26.0.2. Три юнит-теста, четыре TestKit-теста на настоящих JDK
> 21 и 25.0.4 (Linux-машина) — зелёные. Задач ещё нет: они в B-05…B-07.

Половина условий, при которых кэш не будет принят, известна на этапе конфигурации — до запуска
чего-либо (ресёрч §1.1, §1.2, D8). Проверять их в `aotVerify` значит узнавать после тренировки то,
что было видно до неё.

- **Решение: отдельный модуль `zavarnik-gradle-plugin` на `java-gradle-plugin` и `kotlin-dsl`,
  конвенции — [sborka](https://github.com/youndie/sborka)** (то же, что у viddik-плагина).
  Расширение `zavarnik { }`, DSL из ресёрча §2 (D9), задачи регистрируются лениво.
- **Проверки при конфигурации, все — ошибка сборки с причиной:** JDK тулчейна < 25 (одношаговый
  режим появился в 25, JEP 514); `-XX:+UseZGC` в `jvmArgs` при JDK < 26 (R11); классы на
  classpath из каталогов (E4) — то есть плагин требует `application`. Предупреждение: JDK 25.0.0–25.0.3
  и 26.0.0–26.0.1 — JVM не заметит подмену jar (JDK-8377932).
- Отвергнуто: определять версию JDK по `java -version` в момент задачи — тулчейн Gradle уже знает
  её на конфигурации, и ошибка должна прийти раньше `installDist`.
- Не покрывает: сами задачи — [B-05](B-05-aot-train-task.md), [B-06](B-06-aot-verify-task.md).

- AC: проект с `id("…zavarnik")` и тулчейном 21 падает на конфигурации с текстом про JDK 25.
- AC: `jvmArgs("-XX:+UseZGC")` при тулчейне 25 — ошибка с ссылкой на JEP 516.
- AC: тулчейн 25.0.2 — предупреждение с номером JDK-8377932 в выводе.
- Якоря: `zavarnik-gradle-plugin/src/main/kotlin/io/github/youndie/zavarnik/ConfigurationChecks.kt`,
  `zavarnik-gradle-plugin/src/main/kotlin/io/github/youndie/zavarnik/JdkVersion.kt`,
  `zavarnik-gradle-plugin/src/functionalTest/kotlin/io/github/youndie/zavarnik/ConfigurationChecksFunctionalTest.kt`,
  `docs/research/research-architecture.md` (§2, D8–D9).

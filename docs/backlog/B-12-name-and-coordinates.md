---
id: B-12
title: "Имя плагина и координаты Maven: io.github.youndie.zavarnik или website.kotlin.leyden"
status: done
priority: P0
size: XS
stage: stage-4-release
---

# B-12 — Имя плагина и координаты Maven

Бриф спрашивает, помещается ли плагин в пространство `kotlin.website`. У портфеля есть
прецедент: viddik публикует Gradle-плагин как `io.github.youndie.viddik`, а библиотеки идут на
Maven Central через общий воркфлоу sborka. Имя `zavarnik` на Plugin Portal и среди репозиториев
`youndie/*` свободно (ресёрч §1.7); на GitHub есть чужие `zavarnik/zavarnik1` и
`fomina-mv/zavarnik` — не плагины.

- **Рекомендация: `io.github.youndie.zavarnik`**, группа `io.github.youndie`, модуль
  `zavarnik-gradle-plugin` — тот же путь, что у viddik, без новых секретов и без домена в
  координатах. Альтернатива из брифа, `website.kotlin.leyden`, привязывает плагин к сайту и к
  чужому имени проекта (`leyden`); её цена — объяснять это в каждом README.
- **Решено 06.09.2026:** `io.github.youndie.zavarnik`, группа `io.github.youndie`, модуль
  `zavarnik-gradle-plugin`. Ответ владельца — в чате, без оговорок.
- Решено до [B-13](B-13-release-v0-1-and-four-week-watch.md): координаты на Central не
  переписываются.

- AC: ответ владельца записан здесь, `status` переведён в `done`, а имя — в ресёрч §3 (открытый
  вопрос 1) и в `settings.gradle.kts` первого коммита с кодом.
- Якоря: `docs/research/research-architecture.md` (§1.7, §3).

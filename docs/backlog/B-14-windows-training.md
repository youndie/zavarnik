---
id: B-14
title: "Тренировка на Windows: без SIGTERM нужен другой способ закончить прогон"
status: dropped
priority: P3
size: M
stage: stage-3-packaging
---

# B-14 — Тренировка на Windows

> **Снята 07.09.2026** — решение владельца: тренировка на Windows не нужна; ограничение
> записано в README (Requirements).

Ресёрч проверил завершение тренировки только на macOS и Linux: `SIGTERM` пишет кэш (E2), и
`Runtime.halt` тоже (E1). На Windows сигнала нет, `destroy()` у `Process` — `TerminateProcess`,
то есть аналог `SIGKILL`, после которого кэша не будет (E3). Бриф допускает «Windows не
поддерживается в MVP» — так и записано в ресёрче, §3, риск 5.

- **Кандидаты:** `exitAfter` (таймер — единственный режим без сигнала), либо HTTP-эндпоинт
  остановки, который приложение открывает только под `-Dzavarnik.training=true` (то, что делают
  Compose и Nucleus: `System.exit` изнутри). Второе требует строки в приложении — того, чего бриф
  хотел избежать.
- Не покрывает: прод-запуск на Windows — он работает, скрипт `bin/<app>.bat` получает
  `%APP_HOME%` в [B-07](B-07-start-scripts-and-distribution-wiring.md).

- AC: `aotTrain` на Windows-раннере создаёт `app.aot` хотя бы в режиме `exitAfter`; документация
  называет ограничение.
- Якоря: `experiments/aot-validation/run.sh` (E1–E3), `docs/research/research-architecture.md` (§1.3, риск 5).

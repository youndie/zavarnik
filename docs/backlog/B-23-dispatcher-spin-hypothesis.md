---
id: B-23
title: "36 % CPU /business — опрос очереди LimitedDispatcher: артефакт закрепления на 8 ядрах или свойство CIO?"
status: done
priority: P1
size: S
stage: stage-5-optimizer
---

# B-23 — Спин очереди диспетчера

> **Сделано 07.09.2026 — гипотеза опровергнута.** Три конфигурации, `/business`, CPU-профиль
> 60 с: `taskset` 0–7 — 36 % в очереди диспетчера; плюс `-XX:ActiveProcessorCount=8` — 31 %
> в `LockFreeTaskQueueCore.removeFirstOrNull`; без `taskset` — 33 % в
> `LimitedDispatcher.obtainTaskOrDeallocateWorker` + 9 %. Спин — свойство CIO под этой
> нагрузкой, не oversubscription. Доля пользовательского CPU в трёх прогонах 1,7–2,0 %. Куда
> нести — решение владельца (ничего внешнего без него).

Ресёрч §1.4: `LockFreeTaskQueue.removeFirstOrNull` 30,1 % + `LockFreeTaskQueueCore` 5,9 % self
CPU на `/business`; в R8-прогоне — 19,1 % + 4,5 %. Это kotlinx.coroutines, ни один из движков
брифа сюда не дотягивается, но это самая крупная цифра во всём профиле, и её природа неизвестна.

- **Гипотеза:** JVM закреплена на ядрах 0–7, а `Runtime.availableProcessors()` в WSL2 отдаёт
  20 — пул `Dispatchers.IO` и CIO размерности считают от 20, и лишние воркеры крутят очередь.
  Проверка: тот же замер с `-XX:ActiveProcessorCount=8` и без `taskset`; смотреть долю спина и rps.
- Если спин остаётся — это находка про Ktor CIO/coroutines для их трекера (по решению владельца,
  B-17 того же рода), не про плагин.

- AC: строка в ресёрче: доля `removeFirstOrNull` в трёх конфигурациях (taskset 8 / ActiveProcessorCount=8 / без ограничений).
- Якоря: `bench/profile/run.sh`, `bench/profile/results/baseline/business.cpu.collapsed`.

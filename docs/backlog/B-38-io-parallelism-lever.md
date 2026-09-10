---
id: B-38
title: "Рычаг kotlinx.coroutines.io.parallelism: если очередь стоит трети CPU, что её снимает"
status: open
priority: P1
size: S
stage: stage-7-engines
blocked_by: [B-37]
---

# B-38 — Механизм и рычаг

`Dispatchers.IO` на JVM — это `UnlimitedIoScheduler.limitedParallelism(max(64, availableProcessors))`,
то есть `LimitedDispatcher` с одной `LockFreeTaskQueue` и до 64 воркер-циклов, опрашивающих её
(`Dispatcher.kt`, `LimitedDispatcher.kt` в kotlinx.coroutines 1.11.0). CIO отправляет туда каждый
вызов. Если наблюдаемая треть CPU — это состязание 64 воркеров за голову одной очереди, то
уменьшение параллелизма до числа доступных ядер обязано её уменьшить, а на Netty и Jetty не
изменить ничего.

- **Решение: проверять рычагом, а не рассуждением.** Свойство `-Dkotlinx.coroutines.io.parallelism`
  документировано и доступно любому сервису; замер — тот же протокол, `/business`, CPU-профиль
  плюс A/B пропускной способности с чередованием.
- Отрицательный исход тоже ответ: если доля очереди не меняется, объяснение «состязание за
  очередь» неверно, и механизм надо искать в другом месте (например, в частоте отправок на
  запрос, а не в числе воркеров).
- Не покрывает: правку kotlinx.coroutines или Ktor; наружу ничего не уходит без решения владельца
  ([B-17](B-17-r8-invokespecial-rebinding-upstream.md), [B-23](B-23-dispatcher-spin-hypothesis.md)).

- AC: строка в ресёрче: доля очереди и rps при `io.parallelism` = 8, 16 и по умолчанию, плюс
  контроль на Netty.
- Якоря: `bench/profile/engines.sh`, `docs/research/research-engines.md`.

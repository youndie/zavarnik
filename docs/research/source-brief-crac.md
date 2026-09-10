---
id: source-brief-crac
title: Исходный бриф третьей фазы — zavarnik для CRaC
type: research
status: active
date: 2026-09-11
---

> Бриф третьей фазы — как он был сформулирован владельцем 11.09.2026, до ресёрча, **без правок**.
> [research-crac](research-crac.md) отмечает, какие его посылки подтвердились, какие
> перевернулись и какие остались гипотезами. Анкер кода: `experiments/crac-smoke/run.sh` и
> `experiments/crac-ktor/run.sh` — проверки, которые из него выросли.

# zavarnik для CRaC

Та же форма — тренировка, проверка, упаковка — но checkpoint после прогрева, а не кэш классов.
CRaC даёт то, чего Leyden не даёт: прогретый JIT, ready в сотни миллисекунд. Никто не описал,
что ломается на Ktor CIO + HikariCP + Exposed при restore (открытые сокеты, пул соединений,
Random). Риск: нужен CRaC-JDK (Zulu/Liberica), только Linux. Но исследование «что ломается»
ценно даже с отрицательным результатом.

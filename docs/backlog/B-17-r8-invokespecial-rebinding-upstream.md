---
id: B-17
title: "R8 ломает invokespecial на default-методы интерфейсов Kotlin — заводить ли issue в r8"
status: question
priority: P2
size: XS
stage: stage-5-optimizer
---

# B-17 — R8 и `invokespecial` на не-прямой суперинтерфейс: issue в r8?

Ресёрч §1.3: R8 9.4.17 и 9.5.10-dev в режиме `--classfile` переписывают
`invokespecial CompletableDeferred.cancel` в `invokespecial Job.cancel` (member rebinding), и
верификатор JDK 25 отвергает класс — «interface method to invoke is not in a direct
superinterface». Воспроизводится на любом Kotlin-интерфейсе с `-Xjvm-default=all` и
`$jd`-аксессорами (coroutines, Ktor); `-dontoptimize`, `-dontshrink`, `--no-desugaring` не
помогают. Минимальный репродьюсер — `bench/profile/r8.sh` на `bench/`, `javap` до и после.

- Вопрос владельцу: заводить ли issue в `r8.googlesource.com` (и с каким репродьюсером —
  двумя Kotlin-интерфейсами вместо всего Ktor). Чужой трекер — только по решению владельца.
- Не покрывает: обход внутри этого проекта — его нет, и искать его не задача этой фазы.

- AC: решение записано здесь; если «да» — ссылка на issue и минимальный репродьюсер в
  `experiments/`.
- Якоря: `bench/profile/r8.pro`, `bench/profile/results/r8-attempts/`, `docs/research/research-optimizer.md` (§1.3).

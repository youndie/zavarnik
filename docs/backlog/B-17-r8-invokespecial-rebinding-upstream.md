---
id: B-17
title: "R8 ломает invokespecial на default-методы интерфейсов Kotlin — issue в трекере R8"
status: done
priority: P2
size: XS
stage: stage-5-optimizer
---

# B-17 — R8 и `invokespecial` на не-прямой суперинтерфейс: issue заведена

> **Снята 07.09.2026, возвращена и сделана 08.09.2026** — владелец переменил решение и завёл
> issue: <https://issuetracker.google.com/issues/558351430>. Трекер читается только после входа,
> так что подтвердить содержимое issue со стороны нельзя — адрес отвечает, и всё.

Ресёрч §1.3: R8 9.4.17 и 9.5.10-dev в режиме `--classfile` переписывают
`invokespecial CompletableDeferred.cancel` в `invokespecial Job.cancel` (member rebinding), и
верификатор JDK 25 отвергает класс — «interface method to invoke is not in a direct
superinterface». Воспроизводится на любом Kotlin-интерфейсе с `-Xjvm-default=all` и
`$jd`-аксессорами (coroutines, Ktor); `-dontoptimize`, `-dontshrink`, `--no-desugaring` не
помогают. Минимальный репродьюсер — `bench/profile/r8.sh` на `bench/`, `javap` до и после.

**Что ушло в issue 08.09.2026.** Прогон повторён с `-Dcom.android.tools.r8.dumpinputtofile=`,
и дамп приложен к issue — 66 МБ, из них 62,7 МБ приходится на `library.jar` (модули JDK, потому
что `--lib $JAVA_HOME`), сам вход — `program.jar` 11,5 МБ. В теле: полный стек `VerifyError`
вместе с байтами `2ab7 002a b1`, обе половины `javap` (`invokespecial #19 // InterfaceMethod
cancel:()V` до и `invokespecial #42 // InterfaceMethod kotlinx/coroutines/Job.cancel:()V` после)
и цепочка `CompletableDeferred → Deferred → Job`, из которой видно, что `Job` — не прямой
суперинтерфейс. Версия названа явно — 9.4.17, — и то, что это classfile-выход, а не DEX,
подтверждается не словами, а `build.properties` из самого дампа: `backend=CF`.

- Не покрывает: обход внутри этого проекта — его нет, и искать его не задача этой фазы.
  Реакции R8 на issue тоже нет — на 08.09.2026 она только заведена.

- AC (выполнено): ссылка на issue здесь; репродьюсер — `bench/profile/r8.sh` и `r8.pro`, они
  названы в теле issue, отдельного минимального проекта в `experiments/` заводить не стали.
- Якоря: `bench/profile/r8.pro`, `bench/profile/results/r8-attempts/`, `docs/research/research-optimizer.md` (§1.3).

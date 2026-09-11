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
superinterface». Воспроизводится на Kotlin-интерфейсах, собранных в режиме совместимости —
это умолчание Kotlin 2.4.10 и legacy `-Xjvm-default=all-compatibility`, но **не**
`-Xjvm-default=all` (§1.3, уточнено 11.09.2026); `-dontoptimize`, `-dontshrink`, `--no-desugaring` не
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

**Реакция 11.09.2026.** В issue ответили: выложили репродьюсер (Java плюс трансформация
байткода, потому что в Java-исходнике такой `invokespecial` не выражается) и объяснили, что
аксессор родится в режиме совместимости Kotlin. Это поправило наше же утверждение про
`-Xjvm-default=all` — см. §1.3 ресёрча и таблицу четырёх режимов там же. Исправления R8 на эту
дату нет, есть разбор.

**Ответ отправлен 11.09.2026:** признана ошибка с флагом, приложена таблица шести режимов
(оба написания флага), подтверждение со стороны jar-а и матрица версий ниже. Отдельно сказано
то, чего в их разборе нет: раз аксессор родится по умолчанию, `no-compatibility` чинит только
свой код, а входные jar-ы уже собраны — обхода на стороне потребителя нет.

**Матрица версий, 11.09.2026** — вход взят из приложенного к issue дампа (`program.jar` и
`proguard.config`, 6494 класса), то есть байт в байт то, что у них на руках; текущий `bench` для
этого уже не годится, в нём после фазы движков 90 jar-ов вместо 26.

| R8 | сборка | выход | классов | результат |
|---|---|---|---|---|
| 9.4.17 | `c98358724c7e1cb666ffdf35d0808d9d79545e89` | 7 944 341 Б | 5416 | `VerifyError` |
| 9.5.10-dev | `9ffebda8006e09874f7eb5c65fff128d58025ecd` | 8 198 425 Б | 5416 | `VerifyError` |
| 9.5.11-dev | `13c885117fc3015e21649eebe52245ade38b093b` | 8 204 583 Б | 5416 | `VerifyError` |

9.5.11-dev вышла уже после подачи — не чинит. Место падения во всех трёх одно:
`CompletableDeferred.access$cancel$jd` @1, байты `2ab7 002a b1`, JDK 25.0.4. Прогон 9.4.17 дал
те же 5416 классов, что и 08.09, — это заодно контроль воспроизводимости самого прогона.

- AC (выполнено): ссылка на issue здесь; репродьюсер — `bench/profile/r8.sh` и `r8.pro`, они
  названы в теле issue, отдельного минимального проекта в `experiments/` заводить не стали.
- Якоря: `bench/profile/r8.pro`, `bench/profile/results/r8-attempts/`, `docs/research/research-optimizer.md` (§1.3).

---
id: B-33
title: "Одинаковые Random во всех репликах после restore: что затронуто в Kotlin-сервисе и как это ловить"
status: done
priority: P1
size: S
stage: stage-6-crac
---

# B-33 — Одинаковые случайные числа после restore

Два restore одного снимка дают одну и ту же последовательность `java.util.Random` и
`ThreadLocalRandom`; `SecureRandom()` переинициализируется, `SecureRandom(seed)`, `Random` и
`ThreadLocalRandom` — нет ([research-crac](../research/research-crac.md) §1.3, журнал
`experiments/crac-smoke/results/2026-09-11-crac-twice-*.log`). Для Kotlin-сервиса вопрос —
что стоит на `kotlin.random.Random.Default` и на `ThreadLocalRandom` внутри библиотек.

- **Первая строка карты закрыта 11.09.2026:** `kotlin.random.Random.Default` на stdlib 2.4.10 и
  JDK 25 — это `kotlin.random.jdk8.PlatformThreadLocalRandom` над `java.util.concurrent.ThreadLocalRandom`
  (спрошено рефлексией у живой JVM; платформенная реализация выбирается в рантайме, поэтому чтение
  исходника здесь недостаточно). Значит, весь Kotlin-код на `Random.Default` — в общей у реплик
  последовательности.
**Сделана 11.09.2026.** Карта — таблица в §1.3 research-crac (одиннадцать генераторов, где создан,
повторяется ли, кто на нём стоит), с двумя новыми строками: `SplittableRandom` после restore и
`Math.random()` повторяются; и одной вопреки javadoc — `SecureRandom(byte[])` документирован как не
переинициализируемый, а числа даёт разные, потому что провайдер Linux подмешивает системную
энтропию. Кто стоит на чём — грепом по константным пулам jar-ов образца и konekt
(`experiments/crac-smoke/results/2026-09-11-generator-users-by-jar.log`): Hikari и `exposed-jdbc` —
`ThreadLocalRandom`, `flyway-core` и `kotlin-reflect` — `Random`, `postgresql` — `SecureRandom`.
Сторож — `experiments/crac-smoke/generator-guard.sh`: два restore, красный с именами совпавших;
на образце называет шесть. Перенос сторожа внутрь `cracVerify` — не здесь: пробе нужен код в
процессе приложения, а это отдельное решение о форме (агент или jcmd-команда).

- **Решение:** сначала карта, потом сторож. Карта — по исходникам: остальные генераторы, генераторы в Ktor
  (`generateNonce`?), kotlinx.coroutines (jitter?), Hikari (`housekeeper` jitter), pgjdbc,
  Exposed. Каждая строка — путь и версия. Сторож — D4 research-crac: `cracVerify` делает два
  restore и сравнивает пробу генераторов, снятую раннером внутри процесса; как именно снять —
  часть задачи (`jcmd`-команда, агент или HTTP-проба, которую даёт приложение).
- Альтернатива «плагин переинициализирует генераторы через `Resource`» отвергнута: у
  `ThreadLocalRandom` нет API для переинициализации извне, а чужие `Random` в библиотеках не
  достать.
- Не покрывает: починку в JDK (это upstream; issue — только с согласия владельца).

**Уточнение 11.09.2026 (после B-32).** Правило оказалось тоньше: у двух restore одного снимка
совпадают `Random`, созданный до снимка, `ThreadLocalRandom` старого потока **и нового потока
тоже** (сеятель для новых потоков лежит в снимке); различаются `new Random()` после restore и
`SecureRandom()` — `experiments/crac-smoke/randoms.sh`, §1.3 research-crac. Но на konekt пять
restore дали **пять разных** кодов активации eSIM: `MockSmDpPlus` берёт `kotlin.random.Random.Default`,
последовательность у реплик общая, а потребление — нет, потому что до выдачи из того же генератора
черпают вход, токен, экраны, Exposed и Hikari на том воркере, которому досталась заявка.
Отсюда правка D4: проверять надо **генераторы**, а не ответы приложения.

- AC: таблица «генератор — где создаётся — переинициализируется ли — что на нём стоит» в §1.3
  research-crac с адресами; для konekt — ответ по `MockSmDpPlus` из B-32.
- AC: прототип пробы: два restore образца дают одинаковые числа → проверка красная с именем
  генератора; `SecureRandom` — зелёная.
- Якоря: `experiments/crac-smoke/restore-twice.sh`, `experiments/crac-smoke/Hello.java`.

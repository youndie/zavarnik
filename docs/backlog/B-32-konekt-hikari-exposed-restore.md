---
id: B-32
title: "Ворота третьей фазы: konekt (Ktor CIO + HikariCP + Exposed + Postgres) под checkpoint/restore на стенде"
status: open
priority: P0
size: M
stage: stage-6-crac
---

# B-32 — Ворота третьей фазы: что ломается у konekt при restore

Образец Ktor CIO восстанавливается за 50 мс одним правилом для слушающего сокета
([research-crac](../research/research-crac.md) §1.4). У настоящего сервиса сверх этого — пул
соединений (Hikari 7.1.0, 10 соединений), Exposed 1.5.0 поверх него, pgjdbc, Flyway при старте,
брокер, и генераторы случайных чисел. Что делает эта связка при checkpoint с открытыми
соединениями и после restore — не описано нигде (§1.6: у Hikari открыт #2082, у Exposed и pgjdbc
пусто), и это ровно вопрос брифа.

- **Решение:** тот же приём, что у `scripts/aot-image.sh` konekt — контейнер образа на сети
  стенда, но базовый образ `azul/zulu-openjdk:25-jre-crac`, `jcmd … JDK.checkpoint` после
  тренировочной нагрузки под токеном, restore в новом контейнере на той же сети; журнал —
  в konekt `docs/research/measurements-<дата>/crac/`, факты — в §1.8 research-crac.
- Гипотезы, проверяемые в порядке от дешёвой к дорогой, каждая с ожидаемым отказом:
  1. Checkpoint с пулом без настроек — `CheckpointOpenSocketException` на 10 соединениях pgjdbc
     (ожидаемо).
  2. `allowPoolSuspension=true` + `suspendPool()` + `softEvictConnections()` перед checkpoint,
     как у Micronaut, с ожиданием закрытия (500 мс по #2082) — checkpoint проходит?
  3. Политика `type: SOCKET, remotePort: 5432, action: close` вместо кода — проходит? Что видит
     Exposed после restore: `Database.connect(dataSource)` держит `DataSource`, а не соединения —
     первая транзакция берёт новое соединение из пула (`resumePool`) или получает мёртвое?
  4. Restore, когда Postgres перезапущен между checkpoint и restore (другой процесс, тот же
     адрес) — пул восстанавливается или отдаёт `08006`?
  5. `DB_URL`/секреты: прочитаны до checkpoint из окружения тренировочного контейнера —
     доходит ли окружение restore-контейнера до процесса (риск 3)?
  6. `MockSmDpPlus` на `Random.Default`: два restore — одинаковые ответы? (риск 4, вместе с B-33.)
  7. Таймауты Hikari (`maxLifetime`, `keepaliveTime`) и корутинные таймеры после паузы в час
     между checkpoint и restore (риск 6).
- Не покрывает: чарт и кластер (открытый вопрос 2), плагин (B-34).

- AC: журнал с исходом каждой гипотезы (1–7) и коротким выводом «зелёный/красный» по критерию
  §4 research-crac; §1.8 research-crac заполнен с адресами; если красный — список библиотек,
  которым нужен патч, с цитатой отказа.
- AC: готовность konekt по `docker run` → `/health` и первый экран под токеном, медиана
  10 restore против 10 обычных стартов на том же образе — как в `measurements-2026-09-07/aot/`.
- Якоря: `konekt/scripts/aot-image.sh`, `konekt/shared/db/src/main/kotlin/io/konekt/db/DatabaseFactory.kt`,
  `konekt/feature/esim-server-data/src/main/kotlin/io/konekt/feature/esim/server/data/MockSmDpPlus.kt`,
  `experiments/crac-ktor/policies.yaml`.

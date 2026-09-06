---
id: B-10
title: "Docker и Jib: рецепт образа, в котором кэш принимается, и aotVerify внутри контейнера"
status: open
priority: P1
size: M
stage: stage-3-packaging
blocked_by: [B-07]
---

# B-10 — Docker и Jib

Ресёрч проверил кирпичи по одному: `COPY` сохраняет mtime (D3, Docker 29.1.3), перенос по
абсолютному пути переживает удаление тренировочного каталога (D1), нормализованный mtime
переживает копирование (D2). Не проверено, что кирпичи складываются в образ, — и это ровно
«развёрнуто ≠ проверено».

- **Решение: два рецепта в документации и оба — в CI.** Dockerfile: `COPY build/install/<app>
  /opt/<app>` в образ на том же JDK-билде, `CMD ["/opt/<app>/bin/<app>"]`; Jib:
  `jib.container.filesModificationTime` оставить умолчанием `EPOCH_PLUS_SECOND` — та же
  константа, к которой `aotTrain` приводит jar-ы (D3 ресёрча), поэтому ничего настраивать не
  нужно.
- **Проверка внутри контейнера — тем же `aotVerify`**: запуск образа с `JAVA_OPTS=-XX:AOTMode=on
  -Xlog:class+load` и подсчёт `shared objects file` из его stdout. Отвергнуто: доверять, что «на
  хосте прошло».
- Открыто: JDK внутри образа обязан быть **тем же билдом** (`_jvm_ident`, §1.1) — рецепт пинует
  образ по дайджесту, и это надо сказать в документации громко.
- Не покрывает: Kubernetes-манифесты; GraalVM.

- AC: `docker run` образа из рецепта печатает `source: shared objects file` для класса
  приложения; образ с `touch`-нутым jar — `timestamp has changed`.
- AC: Jib-образ с умолчаниями — то же, без единой настройки времени.
- Якоря: `experiments/aot-validation/run.sh` (D1–D3), `docs/research/research-architecture.md` (§1.5, D3).

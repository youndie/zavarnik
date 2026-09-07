# runner-jre — тренировка и проверка в JRE-стадии образа без Gradle (B-25)

Рецепт `samples/ktor/Dockerfile` после B-25: стадия сборки `eclipse-temurin:25.0.4_7-jdk`
делает только `installDist`; стадия рантайма `eclipse-temurin:25.0.4_7-jre` копирует
дистрибутив и запускает `lib/zavarnik-runner.jar` — `train`, затем `verify` — на своей же JVM.
Ни Gradle, ни `curl` в стадии рантайма нет.

Что измерено (Linux-машина, Docker 29.1.3, 07.09.2026):

- `results/docker-check.log` — `docker-check.sh` зелёный: под `-XX:AOTMode=on` контейнер стартует,
  20 из 20 классов `sample.*` и 3818 классов всего из кэша; образ **562 МБ** против 647 МБ у
  рецепта с обеими стадиями на `-jdk`; кэш 31 МБ, раннер 1.9 МБ.
- `results/verify-in-container.log` — `verify` внутри контейнера на подменённом jar падает с тем же
  текстом, что `aotVerify` («the jars in lib/ are not the ones app.aot was trained against»),
  `exit=1`; на нетронутом образе — 2259 из 2259 классов приложения из кэша, `exit=0`.

Воспроизвести: `samples/ktor/docker-check.sh` из корня репозитория, затем две команды из
`results/verify-in-container.log`.

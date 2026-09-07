package io.github.youndie.zavarnik.runner

/** A failure the runner can explain: the message is the whole story, for a build log or a Docker layer alike. */
public class RunnerException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

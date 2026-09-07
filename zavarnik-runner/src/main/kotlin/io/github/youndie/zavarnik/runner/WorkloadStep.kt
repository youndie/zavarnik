package io.github.youndie.zavarnik.runner

/** One workload step: either an HTTP request or an external command. A plain value, so it can be a task input. */
public data class WorkloadStep(
    val command: List<String>,
    val method: String,
    val url: String,
    val contentType: String?,
    val body: String?,
) : java.io.Serializable {
    /** `true` for an external command, `false` for an HTTP request. */
    val isCommand: Boolean get() = command.isNotEmpty()

    override fun toString(): String = if (isCommand) command.joinToString(" ") else "$method $url"

    public companion object {
        public fun http(
            method: String,
            url: String,
            contentType: String?,
            body: String?,
        ): WorkloadStep = WorkloadStep(emptyList(), method, url, contentType, body)

        public fun command(command: List<String>): WorkloadStep = WorkloadStep(command, "", "", null, null)
    }
}

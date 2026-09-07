package io.github.youndie.zavarnik.runner

/**
 * One workload step: either an HTTP request or an external command. A plain value, so it can be a
 * task input.
 *
 * A request may carry [headers] and may [captures] values out of its JSON response into named
 * variables; every later step may use them as `{{name}}` in its URL, headers, body or command
 * words. That is what makes a signed-in workload possible without curl and without a script:
 * request a code, read it, exchange it for a token, send the token.
 */
public data class WorkloadStep(
    val command: List<String>,
    val method: String,
    val url: String,
    val contentType: String?,
    val body: String?,
    val headers: Map<String, String> = emptyMap(),
    /** Variable name → dot path into the JSON response (`accessToken`, `user.id`, `items.0.sku`). */
    val captures: Map<String, String> = emptyMap(),
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
            headers: Map<String, String> = emptyMap(),
            captures: Map<String, String> = emptyMap(),
        ): WorkloadStep = WorkloadStep(emptyList(), method, url, contentType, body, headers, captures)

        public fun command(command: List<String>): WorkloadStep = WorkloadStep(command, "", "", null, null)
    }
}

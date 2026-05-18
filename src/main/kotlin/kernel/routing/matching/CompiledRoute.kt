package kernel.routing.matching

import kernel.http.Request

internal data class CompiledRoute(
    val scheme: String,
    val method: String,
    val path: String,
    val compiledPath: CompiledPath,
    val middleware: List<String>,
    val name: String?,
    val requestType: Class<out Request>?,
    val action: (Map<String, String>) -> Any?
)


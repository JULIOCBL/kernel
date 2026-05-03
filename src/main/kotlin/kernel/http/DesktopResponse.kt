package kernel.http

/**
 * Resultado del pipeline desktop.
 */
sealed class DesktopResponse {
    data class Success(val request: DesktopRequest) : DesktopResponse()

    data class Redirect(
        val target: String,
        val reason: String? = null
    ) : DesktopResponse()

    data class Aborted(
        val reason: String
    ) : DesktopResponse()
}

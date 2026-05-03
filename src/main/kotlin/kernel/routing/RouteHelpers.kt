package kernel.routing

import kernel.http.DesktopResponse

fun route(
    name: String,
    params: Map<String, String> = emptyMap()
): String {
    return Route.route(name, params)
}

fun redirect(target: String? = null): DesktopRedirector {
    return DesktopRedirector(target)
}

class DesktopRedirector internal constructor(
    private val target: String? = null
) {
    fun to(pathOrUri: String = target ?: error("Debes indicar un destino para la redirección.")): DesktopResponse.Redirect {
        return DesktopResponse.Redirect(target = pathOrUri)
    }

    fun route(
        name: String,
        params: Map<String, String> = emptyMap()
    ): DesktopResponse.Redirect {
        return DesktopResponse.Redirect(target = kernel.routing.route(name, params))
    }
}

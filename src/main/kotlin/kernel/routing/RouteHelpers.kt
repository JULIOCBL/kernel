package kernel.routing

import kernel.http.DesktopResponse

fun route(
    name: String,
    params: Map<String, String> = emptyMap()
): String {
    return Route.route(name, params)
}

fun desktopRoute(
    name: String,
    params: Map<String, String> = emptyMap()
): String {
    return Route.desktopRoute(name, params)
}

fun apiRoute(
    name: String,
    params: Map<String, String> = emptyMap()
): String {
    return Route.apiRoute(name, params)
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
        return DesktopResponse.Redirect(target = kernel.routing.desktopRoute(name, params))
    }
}

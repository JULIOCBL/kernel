package kernel.routing

/**
 * Navegador programatico para rutas desktop.
 *
 * Permite que la UI navegue por path o URI completa sin conocer el detalle del
 * router, del dispatcher ni del estado observable interno.
 */
class DesktopNavigator(
    private val scheme: String,
    private val router: DesktopRouter,
    private val routeState: RouteStateStore<RouteResolution>,
    private val viewState: RouteStateStore<DesktopView>,
    private val viewDispatcher: DesktopViewDispatcher,
    private val onNavigation: (RouteResolution, DesktopView, String, String) -> Unit
) {
    fun navigate(pathOrUri: String): DesktopView? {
        return navigate(pathOrUri, "runtime.current_route", "runtime.current_view")
    }

    fun navigateInitial(pathOrUri: String): DesktopView? {
        return navigate(pathOrUri, "runtime.initial_route", "runtime.initial_view")
    }

    fun navigate(
        pathOrUri: String,
        routeConfigKey: String,
        viewConfigKey: String
    ): DesktopView? {
        val resolution = router.resolve(toDesktopUri(pathOrUri)) ?: return null
        val view = viewDispatcher.dispatch(resolution) ?: return null
        routeState.update(resolution)
        viewState.update(view)
        onNavigation(resolution, view, routeConfigKey, viewConfigKey)
        return view
    }

    private fun toDesktopUri(pathOrUri: String): String {
        val trimmed = pathOrUri.trim()
        if (trimmed.startsWith("$scheme://")) {
            return trimmed
        }

        val normalizedPath = when {
            trimmed.isBlank() -> "/"
            trimmed.startsWith("/") -> trimmed
            else -> "/$trimmed"
        }

        return "$scheme://$normalizedPath"
    }
}

package kernel.routing

/**
 * Navegador programatico para rutas desktop.
 *
 * Permite que la UI navegue por path o URI completa sin conocer el detalle del
 * router, del dispatcher ni del estado observable interno.
 */
class DesktopNavigator(
    private val links: LinkGenerator,
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
        val resolution = router.resolve(links.desktop(pathOrUri)) ?: return null
        val view = viewDispatcher.dispatch(resolution) ?: return null
        routeState.update(resolution)
        viewState.update(view)
        onNavigation(resolution, view, routeConfigKey, viewConfigKey)
        return view
    }
}

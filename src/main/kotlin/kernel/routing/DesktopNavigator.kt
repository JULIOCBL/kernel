package kernel.routing

import kernel.foundation.Application
import kernel.foundation.DesktopKernel
import kernel.http.DesktopRequest
import kernel.http.DesktopResponse

/**
 * Navegador programatico para rutas desktop.
 *
 * Permite que la UI navegue por path o URI completa sin conocer el detalle del
 * router, del dispatcher ni del estado observable interno.
 */
class DesktopNavigator(
    private val app: Application,
    private val links: LinkGenerator,
    private val router: DesktopRouter,
    private val desktopKernel: DesktopKernel,
    private val routeState: RouteStateStore<RouteResolution>,
    private val viewState: RouteStateStore<DesktopView>,
    private val viewDispatcher: DesktopViewDispatcher,
    private val onNavigation: (RouteResolution, DesktopView, String, String) -> Unit
) {
    fun navigate(pathOrUri: String): DesktopView? {
        return navigate(pathOrUri, "runtime.current_route", "runtime.current_view")
    }

    fun navigateInitial(pathOrUri: String): DesktopView? {
        val view = navigate(pathOrUri, "runtime.current_route", "runtime.current_view")
        val resolution = routeState.current()

        if (view != null && resolution != null) {
            onNavigation(resolution, view, "runtime.initial_route", "runtime.initial_view")
        }

        return view
    }

    fun navigate(
        pathOrUri: String,
        routeConfigKey: String,
        viewConfigKey: String
    ): DesktopView? {
        return navigate(pathOrUri, routeConfigKey, viewConfigKey, redirectDepth = 0)
    }

    private fun navigate(
        pathOrUri: String,
        routeConfigKey: String,
        viewConfigKey: String,
        redirectDepth: Int
    ): DesktopView? {
        if (redirectDepth > 8) {
            error("Se detectó un loop de redirección desktop al navegar hacia `$pathOrUri`.")
        }

        val target = if (links.matches(pathOrUri)) pathOrUri else links.desktop(pathOrUri)
        val resolution = router.resolve(target) ?: return null
        val request = DesktopRequest(
            app = app,
            routeName = resolution.path,
            target = target,
            params = resolution.params,
            resolution = resolution
        )
        val response = desktopKernel.handle(request, resolution.middleware)

        when (response) {
            is DesktopResponse.Success -> {
                app.config.set("runtime.last_navigation_response", response)
            }

            is DesktopResponse.Redirect -> {
                app.config.set("runtime.last_navigation_response", response)
                return navigate(response.target, routeConfigKey, viewConfigKey, redirectDepth + 1)
            }

            is DesktopResponse.Aborted -> {
                app.config.set("runtime.last_navigation_response", response)
                return null
            }
        }

        val view = viewDispatcher.dispatch(resolution) ?: return null
        routeState.update(resolution)
        viewState.update(view)
        onNavigation(resolution, view, routeConfigKey, viewConfigKey)
        return view
    }
}

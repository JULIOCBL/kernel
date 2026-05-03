package kernel.providers

import kernel.foundation.Application
import kernel.foundation.DesktopKernel
import kernel.routing.ApiRouter
import kernel.routing.ControllerRegistry
import kernel.routing.DefaultDesktopViewDispatcher
import kernel.routing.DesktopNavigator
import kernel.routing.DesktopRouter
import kernel.routing.DesktopView
import kernel.routing.DesktopViewDispatcher
import kernel.routing.LinkGenerator
import kernel.routing.RouteModuleLoader
import kernel.routing.RouteResolution
import kernel.routing.RouteStateStore

/**
 * Provider global de rutas del kernel.
 *
 * Crea y carga los routers de `desktop` y `api` siguiendo una convención de
 * archivos estilo Laravel dentro de `app.namespace.routes`.
 */
open class RouteServiceProvider(app: Application) : ServiceProvider(app) {
    override fun register() {
        val desktopScheme = desktopScheme()
        val apiScheme = apiScheme()
        val desktopRouter = DesktopRouter(desktopScheme)
        val desktopLinks = LinkGenerator(desktopScheme)
        val apiRouter = ApiRouter(apiScheme)
        val desktopRouteState = RouteStateStore<RouteResolution>()
        val desktopViewState = RouteStateStore<DesktopView>()
        val controllerRegistry = ControllerRegistry()
        val desktopKernel =
            app.config.get("services.routes.desktop.kernel") as? DesktopKernel
                ?: DesktopKernel(app)
        val desktopViewDispatcher =
            app.config.get("services.routes.desktop.dispatcher") as? DesktopViewDispatcher
                ?: DefaultDesktopViewDispatcher
        val desktopNavigator = DesktopNavigator(
            app = app,
            links = desktopLinks,
            router = desktopRouter,
            desktopKernel = desktopKernel,
            routeState = desktopRouteState,
            viewState = desktopViewState,
            viewDispatcher = desktopViewDispatcher
        ) { resolution, view, routeConfigKey, viewConfigKey ->
            app.config.set(routeConfigKey, resolution)
            app.config.set(viewConfigKey, view)
        }

        app.config.set("services.routes.desktop.router", desktopRouter)
        app.config.set("services.routes.desktop.links", desktopLinks)
        app.config.set("services.routes.desktop.state", desktopRouteState)
        app.config.set("services.routes.desktop.view_state", desktopViewState)
        app.config.set("services.routes.desktop.kernel", desktopKernel)
        app.config.set("services.routes.desktop.dispatcher", desktopViewDispatcher)
        app.config.set("services.routes.desktop.navigator", desktopNavigator)
        app.config.set("services.routes.api.router", apiRouter)
        app.config.set("services.routes.controllers", controllerRegistry)

        // Alias de compatibilidad mientras se migra el código viejo.
        app.config.set("services.router", desktopRouter)
    }

    override fun boot() {
        val desktopRouter = app.config.get("services.routes.desktop.router") as DesktopRouter
        val apiRouter = app.config.get("services.routes.api.router") as ApiRouter

        RouteModuleLoader.loadDesktopRoutes(app, desktopRouter)
        RouteModuleLoader.loadApiRoutes(app, apiRouter)
    }

    private fun desktopScheme(): String {
        return configuredValue(
            primaryKey = "app.scheme",
            legacyKey = "routing.desktop.scheme",
            default = "kernelplayground"
        )
    }

    private fun apiScheme(): String {
        return configuredValue(
            primaryKey = "routing.api.scheme",
            legacyKey = "app.routing.api.scheme",
            default = "api"
        )
    }

    private fun configuredValue(primaryKey: String, legacyKey: String, default: String): String {
        val primary = app.config.string(primaryKey).trim()
        if (primary.isNotBlank()) {
            return primary
        }

        val legacy = app.config.string(legacyKey).trim()
        if (legacy.isNotBlank()) {
            return legacy
        }

        return default
    }
}

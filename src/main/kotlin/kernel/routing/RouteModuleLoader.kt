package kernel.routing

import kernel.foundation.Application

internal object RouteModuleLoader {
    fun loadDesktopRoutes(app: Application, router: DesktopRouter) {
        invokeLoader(
            app = app,
            defaultClassName = "${routeNamespace(app)}.DesktopKt",
            methodName = "defineDesktopRoutes",
            router = router
        )
    }

    fun loadApiRoutes(app: Application, router: ApiRouter) {
        val loadedInternal = invokeLoader(
            app = app,
            defaultClassName = "${routeNamespace(app)}.InternalKt",
            methodName = "defineInternalRoutes",
            router = router,
            optional = true
        )

        if (loadedInternal) {
            return
        }

        invokeLoader(
            app = app,
            defaultClassName = "${routeNamespace(app)}.ApiKt",
            methodName = "defineApiRoutes",
            router = router
        )
    }

    private fun routeNamespace(app: Application): String {
        val appNamespace = app.config.string("app.namespace").trim()
        require(appNamespace.isNotBlank()) {
            "La app debe definir `app.namespace` para cargar rutas por convencion."
        }

        return "$appNamespace.routes"
    }

    private fun invokeLoader(
        app: Application,
        defaultClassName: String,
        methodName: String,
        router: SchemeRouter,
        optional: Boolean = false
    ): Boolean {
        val configuredClassName = app.config.string(
            "routing.loaders.$methodName.class",
            defaultClassName
        ).trim()
        val configuredMethodName = app.config.string(
            "routing.loaders.$methodName.method",
            methodName
        ).trim()

        val classLoader = Thread.currentThread().contextClassLoader
            ?: RouteModuleLoader::class.java.classLoader

        val routeClass = try {
            Class.forName(configuredClassName, false, classLoader)
        } catch (_: ClassNotFoundException) {
            return false
        }

        val noArgMethod = routeClass.methods.firstOrNull { method ->
            method.name == configuredMethodName && method.parameterCount == 0
        }

        if (noArgMethod != null) {
            Route.withRouter(router) {
                noArgMethod.invoke(null)
            }
            return true
        }

        val routerMethod = routeClass.methods.firstOrNull { method ->
            method.name == configuredMethodName &&
                method.parameterCount == 1 &&
                method.parameterTypes.single().isAssignableFrom(router::class.java)
        }

        if (routerMethod != null) {
            routerMethod.invoke(null, router)
            return true
        }

        if (optional) {
            return false
        }

        error(
            "No se pudo cargar `$configuredClassName#$configuredMethodName`. " +
                "Debe ser una funcion top-level sin argumentos o aceptar ${router::class.simpleName}."
        )
    }
}

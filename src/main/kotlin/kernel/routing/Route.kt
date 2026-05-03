package kernel.routing

import kernel.foundation.Application
import kernel.foundation.ApplicationRuntime
import kotlin.jvm.JvmName
import kotlin.reflect.KClass
import kotlin.reflect.KFunction1
import kotlin.reflect.KFunction2
import kotlin.reflect.jvm.isAccessible

/**
 * Fachada minima estilo Laravel para registrar rutas sin exponer el router
 * concreto dentro de cada archivo `routes/...`.
 */
object Route {
    private val currentRegistrar = ThreadLocal<SchemeRouter?>()

    class PendingRouteRegistration internal constructor(
        private val path: String
    ) {
        private val aliases = linkedSetOf<String>()
        private var routeName: String? = null

        fun middleware(vararg middleware: String): PendingRouteRegistration {
            middleware
                .map(String::trim)
                .filter(String::isNotBlank)
                .forEach(aliases::add)

            return this
        }

        fun name(name: String): PendingRouteRegistration {
            val normalized = name.trim()
            require(normalized.isNotBlank()) {
                "El nombre de ruta no puede estar vacio."
            }

            routeName = normalized
            return this
        }

        fun action(action: () -> Any?) {
            register(path, { action() }, aliases.toList(), routeName)
        }

        fun action(action: (Map<String, String>) -> Any?) {
            register(path, action, aliases.toList(), routeName)
        }
    }

    fun view(
        name: String,
        data: Map<String, Any?> = emptyMap(),
        title: String = name
    ): DesktopView {
        return DesktopView(
            name = name,
            title = title,
            model = data
        )
    }

    fun get(path: String, action: (Map<String, String>) -> Any?) {
        register(path, action)
    }

    fun get(path: String): PendingRouteRegistration {
        return PendingRouteRegistration(path)
    }

    @JvmName("getControllerNoParams")
    fun <T : Any, R> get(path: String, action: KFunction1<T, R>) {
        register(path, {
            val controller = resolveController(action)
            action.isAccessible = true
            action.call(controller)
        })
    }

    @JvmName("getControllerWithParams")
    fun <T : Any, R> get(path: String, action: KFunction2<T, Map<String, String>, R>) {
        register(path, { params ->
            val controller = resolveController(action)
            action.isAccessible = true
            action.call(controller, params)
        })
    }

    @JvmName("getControllerNoParamsWithView")
    fun <T : Any, R> get(
        path: String,
        action: KFunction1<T, R>,
        view: (R) -> Any?
    ) {
        register(path, {
            val controller = resolveController(action)
            action.isAccessible = true
            view(action.call(controller))
        })
    }

    @JvmName("getControllerWithParamsWithView")
    fun <T : Any, R> get(
        path: String,
        action: KFunction2<T, Map<String, String>, R>,
        view: (R) -> Any?
    ) {
        register(path, { params ->
            val controller = resolveController(action)
            action.isAccessible = true
            view(action.call(controller, params))
        })
    }

    @Suppress("UNCHECKED_CAST")
    inline fun <reified T : Any> registerController(noinline factory: (Application) -> T) {
        val app = ApplicationRuntime.current()
        val registry = app.config.get("services.routes.controllers") as? ControllerRegistry
            ?: error("ControllerRegistry no está registrado en services.routes.controllers.")

        registry.register(T::class, factory)
    }

    fun desktop(): DesktopRouter {
        val app = ApplicationRuntime.current()
        return app.config.get("services.routes.desktop.router") as? DesktopRouter
            ?: error("DesktopRouter no esta registrado en services.routes.desktop.router.")
    }

    fun api(): ApiRouter {
        val app = ApplicationRuntime.current()
        return app.config.get("services.routes.api.router") as? ApiRouter
            ?: error("ApiRouter no esta registrado en services.routes.api.router.")
    }

    fun route(
        name: String,
        params: Map<String, String> = emptyMap()
    ): String {
        val app = ApplicationRuntime.current()
        val links = app.config.get("services.routes.desktop.links") as? LinkGenerator
            ?: error("LinkGenerator no esta registrado en services.routes.desktop.links.")

        return links.desktop(desktop().pathFor(name, params))
    }

    internal fun withRouter(router: SchemeRouter, block: () -> Unit) {
        val previous = currentRegistrar.get()
        currentRegistrar.set(router)

        try {
            block()
        } finally {
            currentRegistrar.set(previous)
        }
    }

    private fun register(
        path: String,
        action: (Map<String, String>) -> Any?,
        middleware: List<String> = emptyList(),
        name: String? = null
    ) {
        val router = currentRegistrar.get()
            ?: error(
                "No hay un router activo para registrar la ruta `$path`. " +
                    "Las rutas deben declararse dentro del cargador oficial del kernel."
            )

        router.get(path, action, middleware, name)
    }

    @JvmName("resolveControllerFromKFunction1")
    private fun <T : Any> resolveController(action: KFunction1<T, *>): T {
        return ControllerResolver.resolve(controllerTypeOf(action))
    }

    @JvmName("resolveControllerFromKFunction2")
    private fun <T : Any> resolveController(action: KFunction2<T, *, *>): T {
        return ControllerResolver.resolve(controllerTypeOf(action))
    }

    @Suppress("UNCHECKED_CAST")
    @JvmName("controllerTypeOfKFunction1")
    private fun <T : Any> controllerTypeOf(action: KFunction1<T, *>): KClass<T> {
        return action.parameters.first().type.classifier as? KClass<T>
            ?: error("No se pudo inferir el tipo del controlador para `${action.name}`.")
    }

    @Suppress("UNCHECKED_CAST")
    @JvmName("controllerTypeOfKFunction2")
    private fun <T : Any> controllerTypeOf(action: KFunction2<T, *, *>): KClass<T> {
        return action.parameters.first().type.classifier as? KClass<T>
            ?: error("No se pudo inferir el tipo del controlador para `${action.name}`.")
    }
}

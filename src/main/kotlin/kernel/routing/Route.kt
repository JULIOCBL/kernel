package kernel.routing

import kernel.foundation.Application
import kernel.foundation.ApplicationRuntime
import kernel.database.orm.Model
import kernel.database.orm.ModelDefinition
import kernel.database.orm.ModelNotFoundException
import kernel.http.HttpRequestResolver
import kernel.http.Request
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap
import kotlin.jvm.JvmName
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KFunction1
import kotlin.reflect.KFunction2
import kotlin.reflect.KFunction3
import kotlin.reflect.KParameter
import kotlin.reflect.full.companionObjectInstance
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.jvm.isAccessible

/**
 * Fachada minima estilo Laravel para registrar rutas sin exponer el router
 * concreto dentro de cada archivo `routes/...`.
 */
object Route {
    private val currentRegistrar = ThreadLocal<SchemeRouter?>()
    private val currentGroupStack = ThreadLocal.withInitial { mutableListOf<GroupContext>() }
    private val actionPlanCache = ConcurrentHashMap<KFunction<*>, ControllerActionPlan>()

    private data class GroupContext(
        val prefix: String = "",
        val middleware: List<String> = emptyList(),
        val namePrefix: String = ""
    )

    private data class ControllerActionPlan(
        val requestType: Class<out Request>? = null,
        val resolvers: List<(Map<String, String>) -> Any>
    )

    class RouteGroupBuilder internal constructor(
        private val prefix: String = "",
        private val middleware: List<String> = emptyList(),
        private val namePrefix: String = ""
    ) {
        fun prefix(prefix: String): RouteGroupBuilder {
            return RouteGroupBuilder(
                prefix = joinPathSegments(this.prefix, prefix),
                middleware = middleware,
                namePrefix = namePrefix
            )
        }

        fun name(prefix: String): RouteGroupBuilder {
            val normalized = prefix.trim()
            require(normalized.isNotBlank()) {
                "El prefijo de nombre del grupo no puede estar vacio."
            }

            return RouteGroupBuilder(
                prefix = this.prefix,
                middleware = middleware,
                namePrefix = "$namePrefix$normalized"
            )
        }

        fun middleware(vararg middleware: String): RouteGroupBuilder {
            val aliases = linkedSetOf<String>()
            this.middleware.forEach(aliases::add)
            middleware
                .map(String::trim)
                .filter(String::isNotBlank)
                .forEach(aliases::add)

            return RouteGroupBuilder(
                prefix = prefix,
                middleware = aliases.toList(),
                namePrefix = namePrefix
            )
        }

        fun group(block: () -> Unit) {
            val stack = currentGroupStack.get()
            stack += GroupContext(prefix = prefix, middleware = middleware, namePrefix = namePrefix)

            try {
                block()
            } finally {
                stack.removeLast()
            }
        }
    }

    class PendingRouteRegistration internal constructor(
        private val method: String,
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
            register(method, path, { action() }, aliases.toList(), routeName)
        }

        fun action(action: (Map<String, String>) -> Any?) {
            register(method, path, action, aliases.toList(), routeName)
        }

        @JvmName("pendingActionControllerNoParams")
        fun <T : Any, R> action(action: KFunction1<T, R>) {
            register(method, path, {
                val controller = resolveController(action)
                action.isAccessible = true
                action.call(controller)
            }, aliases.toList(), routeName)
        }

        @JvmName("pendingActionControllerWithOneArgument")
        fun <T : Any, P : Any, R> action(action: KFunction2<T, P, R>) {
            registerControllerAction(method, path, action, aliases.toList(), routeName)
        }

        @JvmName("pendingActionControllerWithTwoArguments")
        fun <T : Any, P1 : Any, P2 : Any, R> action(action: KFunction3<T, P1, P2, R>) {
            registerTwoArgumentControllerAction(method, path, action, aliases.toList(), routeName)
        }

        @JvmName("pendingActionControllerWithManyArguments")
        fun <R> action(action: KFunction<R>) {
            registerGenericControllerAction(method, path, action, aliases.toList(), routeName)
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

    fun prefix(prefix: String): RouteGroupBuilder {
        return RouteGroupBuilder(prefix = prefix.trim())
    }

    fun name(prefix: String): RouteGroupBuilder {
        return RouteGroupBuilder().name(prefix)
    }

    fun middleware(vararg middleware: String): RouteGroupBuilder {
        return RouteGroupBuilder().middleware(*middleware)
    }

    fun group(block: () -> Unit) {
        RouteGroupBuilder().group(block)
    }

    fun get(path: String, action: (Map<String, String>) -> Any?) {
        register("GET", path, action)
    }

    fun get(path: String): PendingRouteRegistration {
        return PendingRouteRegistration("GET", path)
    }

    fun post(path: String, action: (Map<String, String>) -> Any?) {
        register("POST", path, action)
    }

    fun post(path: String): PendingRouteRegistration {
        return PendingRouteRegistration("POST", path)
    }

    fun put(path: String, action: (Map<String, String>) -> Any?) {
        register("PUT", path, action)
    }

    fun put(path: String): PendingRouteRegistration {
        return PendingRouteRegistration("PUT", path)
    }

    fun delete(path: String, action: (Map<String, String>) -> Any?) {
        register("DELETE", path, action)
    }

    fun delete(path: String): PendingRouteRegistration {
        return PendingRouteRegistration("DELETE", path)
    }

    fun patch(path: String, action: (Map<String, String>) -> Any?) {
        register("PATCH", path, action)
    }

    fun patch(path: String): PendingRouteRegistration {
        return PendingRouteRegistration("PATCH", path)
    }

    fun head(path: String, action: (Map<String, String>) -> Any?) {
        register("HEAD", path, action)
    }

    fun head(path: String): PendingRouteRegistration {
        return PendingRouteRegistration("HEAD", path)
    }

    fun options(path: String, action: (Map<String, String>) -> Any?) {
        register("OPTIONS", path, action)
    }

    fun options(path: String): PendingRouteRegistration {
        return PendingRouteRegistration("OPTIONS", path)
    }

    fun trace(path: String, action: (Map<String, String>) -> Any?) {
        register("TRACE", path, action)
    }

    fun trace(path: String): PendingRouteRegistration {
        return PendingRouteRegistration("TRACE", path)
    }

    fun connect(path: String, action: (Map<String, String>) -> Any?) {
        register("CONNECT", path, action)
    }

    fun connect(path: String): PendingRouteRegistration {
        return PendingRouteRegistration("CONNECT", path)
    }

    fun method(method: String, path: String, action: (Map<String, String>) -> Any?) {
        register(method, path, action)
    }

    fun method(method: String, path: String): PendingRouteRegistration {
        return PendingRouteRegistration(method, path)
    }

    @JvmName("getControllerNoParams")
    fun <T : Any, R> get(path: String, action: KFunction1<T, R>) {
        register("GET", path, {
            val controller = resolveController(action)
            action.isAccessible = true
            action.call(controller)
        })
    }

    @JvmName("getControllerWithOneArgument")
    fun <T : Any, P : Any, R> get(path: String, action: KFunction2<T, P, R>) {
        registerControllerAction("GET", path, action)
    }

    @JvmName("getControllerWithTwoArguments")
    fun <T : Any, P1 : Any, P2 : Any, R> get(path: String, action: KFunction3<T, P1, P2, R>) {
        registerTwoArgumentControllerAction("GET", path, action)
    }

    @JvmName("getControllerWithManyArguments")
    fun <R> get(path: String, action: KFunction<R>) {
        registerGenericControllerAction("GET", path, action)
    }

    @JvmName("postControllerWithTwoArguments")
    fun <T : Any, P1 : Any, P2 : Any, R> post(path: String, action: KFunction3<T, P1, P2, R>) {
        registerTwoArgumentControllerAction("POST", path, action)
    }

    @JvmName("postControllerWithManyArguments")
    fun <R> post(path: String, action: KFunction<R>) {
        registerGenericControllerAction("POST", path, action)
    }

    @JvmName("putControllerWithTwoArguments")
    fun <T : Any, P1 : Any, P2 : Any, R> put(path: String, action: KFunction3<T, P1, P2, R>) {
        registerTwoArgumentControllerAction("PUT", path, action)
    }

    @JvmName("putControllerWithManyArguments")
    fun <R> put(path: String, action: KFunction<R>) {
        registerGenericControllerAction("PUT", path, action)
    }

    @JvmName("deleteControllerWithTwoArguments")
    fun <T : Any, P1 : Any, P2 : Any, R> delete(path: String, action: KFunction3<T, P1, P2, R>) {
        registerTwoArgumentControllerAction("DELETE", path, action)
    }

    @JvmName("deleteControllerWithManyArguments")
    fun <R> delete(path: String, action: KFunction<R>) {
        registerGenericControllerAction("DELETE", path, action)
    }

    @JvmName("patchControllerWithTwoArguments")
    fun <T : Any, P1 : Any, P2 : Any, R> patch(path: String, action: KFunction3<T, P1, P2, R>) {
        registerTwoArgumentControllerAction("PATCH", path, action)
    }

    @JvmName("patchControllerWithManyArguments")
    fun <R> patch(path: String, action: KFunction<R>) {
        registerGenericControllerAction("PATCH", path, action)
    }

    @JvmName("headControllerWithTwoArguments")
    fun <T : Any, P1 : Any, P2 : Any, R> head(path: String, action: KFunction3<T, P1, P2, R>) {
        registerTwoArgumentControllerAction("HEAD", path, action)
    }

    @JvmName("headControllerWithManyArguments")
    fun <R> head(path: String, action: KFunction<R>) {
        registerGenericControllerAction("HEAD", path, action)
    }

    @JvmName("optionsControllerWithTwoArguments")
    fun <T : Any, P1 : Any, P2 : Any, R> options(path: String, action: KFunction3<T, P1, P2, R>) {
        registerTwoArgumentControllerAction("OPTIONS", path, action)
    }

    @JvmName("optionsControllerWithManyArguments")
    fun <R> options(path: String, action: KFunction<R>) {
        registerGenericControllerAction("OPTIONS", path, action)
    }

    @JvmName("traceControllerWithTwoArguments")
    fun <T : Any, P1 : Any, P2 : Any, R> trace(path: String, action: KFunction3<T, P1, P2, R>) {
        registerTwoArgumentControllerAction("TRACE", path, action)
    }

    @JvmName("traceControllerWithManyArguments")
    fun <R> trace(path: String, action: KFunction<R>) {
        registerGenericControllerAction("TRACE", path, action)
    }

    @JvmName("connectControllerWithTwoArguments")
    fun <T : Any, P1 : Any, P2 : Any, R> connect(path: String, action: KFunction3<T, P1, P2, R>) {
        registerTwoArgumentControllerAction("CONNECT", path, action)
    }

    @JvmName("connectControllerWithManyArguments")
    fun <R> connect(path: String, action: KFunction<R>) {
        registerGenericControllerAction("CONNECT", path, action)
    }

    @JvmName("postControllerWithRequest")
    fun <T : Any, RQ : Request, R> post(path: String, action: KFunction2<T, RQ, R>) {
        registerRequestAction("POST", path, action)
    }

    @JvmName("putControllerWithRequest")
    fun <T : Any, RQ : Request, R> put(path: String, action: KFunction2<T, RQ, R>) {
        registerRequestAction("PUT", path, action)
    }

    @JvmName("deleteControllerWithRequest")
    fun <T : Any, RQ : Request, R> delete(path: String, action: KFunction2<T, RQ, R>) {
        registerRequestAction("DELETE", path, action)
    }

    @JvmName("patchControllerWithRequest")
    fun <T : Any, RQ : Request, R> patch(path: String, action: KFunction2<T, RQ, R>) {
        registerRequestAction("PATCH", path, action)
    }

    @JvmName("headControllerWithRequest")
    fun <T : Any, RQ : Request, R> head(path: String, action: KFunction2<T, RQ, R>) {
        registerRequestAction("HEAD", path, action)
    }

    @JvmName("optionsControllerWithRequest")
    fun <T : Any, RQ : Request, R> options(path: String, action: KFunction2<T, RQ, R>) {
        registerRequestAction("OPTIONS", path, action)
    }

    @JvmName("traceControllerWithRequest")
    fun <T : Any, RQ : Request, R> trace(path: String, action: KFunction2<T, RQ, R>) {
        registerRequestAction("TRACE", path, action)
    }

    @JvmName("connectControllerWithRequest")
    fun <T : Any, RQ : Request, R> connect(path: String, action: KFunction2<T, RQ, R>) {
        registerRequestAction("CONNECT", path, action)
    }

    @JvmName("getControllerNoParamsWithView")
    fun <T : Any, R> get(
        path: String,
        action: KFunction1<T, R>,
        view: (R) -> Any?
    ) {
        register("GET", path, {
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
        register("GET", path, { params ->
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
        val desktopRouter = runCatching { desktop() }.getOrNull()
        val apiRouter = runCatching { api() }.getOrNull()
        val currentRequestScheme = runCatching {
            kernel.http.HttpRequestRuntime.current().target.substringBefore("://")
        }.getOrNull()?.lowercase()

        if (currentRequestScheme == apiRouter?.scheme()?.lowercase() && apiRouter?.hasNamedRoute(name) == true) {
            return apiRoute(name, params)
        }

        if (currentRequestScheme == desktopRouter?.scheme()?.lowercase() && desktopRouter?.hasNamedRoute(name) == true) {
            return desktopRoute(name, params)
        }

        if (desktopRouter?.hasNamedRoute(name) == true) {
            return desktopRoute(name, params)
        }

        if (apiRouter?.hasNamedRoute(name) == true) {
            return apiRoute(name, params)
        }

        error("No existe una ruta nombrada `$name` ni en desktop ni en api.")
    }

    fun desktopRoute(
        name: String,
        params: Map<String, String> = emptyMap()
    ): String {
        val app = ApplicationRuntime.current()
        val links = app.config.get("services.routes.desktop.links") as? LinkGenerator
            ?: error("LinkGenerator no esta registrado en services.routes.desktop.links.")

        return links.desktop(desktop().pathFor(name, params))
    }

    fun apiRoute(
        name: String,
        params: Map<String, String> = emptyMap()
    ): String {
        return api().pathFor(name, params)
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
        method: String,
        path: String,
        action: (Map<String, String>) -> Any?,
        middleware: List<String> = emptyList(),
        name: String? = null,
        requestType: Class<out Request>? = null
    ) {
        val router = currentRegistrar.get()
            ?: error(
            "No hay un router activo para registrar la ruta `$path`. " +
                    "Las rutas deben declararse dentro del cargador oficial del kernel."
            )

        val groupContext = currentGroupContext()
        val resolvedPath = joinPathSegments(groupContext.prefix, path)
        val resolvedMiddleware = mergeMiddleware(groupContext.middleware, middleware)
        val resolvedName = name?.let { "${groupContext.namePrefix}$it" }

        when (method.uppercase()) {
            "GET" -> router.get(resolvedPath, action, resolvedMiddleware, resolvedName, requestType)
            "POST" -> router.post(resolvedPath, action, resolvedMiddleware, resolvedName, requestType)
            "PUT" -> router.put(resolvedPath, action, resolvedMiddleware, resolvedName, requestType)
            "DELETE" -> router.delete(resolvedPath, action, resolvedMiddleware, resolvedName, requestType)
            "PATCH" -> router.patch(resolvedPath, action, resolvedMiddleware, resolvedName, requestType)
            "HEAD" -> router.head(resolvedPath, action, resolvedMiddleware, resolvedName, requestType)
            "OPTIONS" -> router.options(resolvedPath, action, resolvedMiddleware, resolvedName, requestType)
            "TRACE" -> router.trace(resolvedPath, action, resolvedMiddleware, resolvedName, requestType)
            "CONNECT" -> router.connect(resolvedPath, action, resolvedMiddleware, resolvedName, requestType)
            else -> router.method(method, resolvedPath, action, resolvedMiddleware, resolvedName, requestType)
        }
    }

    private fun <T : Any, RQ : Request, R> registerRequestAction(
        method: String,
        path: String,
        action: KFunction2<T, RQ, R>,
        middleware: List<String> = emptyList(),
        name: String? = null
    ) {
        val requestType = requestTypeOf(action)
        register(method, path, {
            val controller = resolveController(action)
            val request = HttpRequestResolver.resolve(requestType)
            action.isAccessible = true
            action.call(controller, request)
        }, middleware, name, requestType.java)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any, P : Any, R> registerControllerAction(
        method: String,
        path: String,
        action: KFunction2<T, P, R>,
        middleware: List<String> = emptyList(),
        name: String? = null
    ) {
        val parameterType = action.parameters[1].type.classifier as? KClass<*>
            ?: error("No se pudo inferir el parametro de `${action.name}`.")

        when {
            parameterType == Map::class -> {
                register(method, path, { params ->
                    val controller = resolveController(action)
                    action.isAccessible = true
                    action.call(controller, params)
                }, middleware, name)
            }
            parameterType.isSubclassOf(Request::class) -> {
                registerRequestAction(
                    method,
                    path,
                    action as KFunction2<T, Request, R>,
                    middleware,
                    name
                )
            }
            parameterType.isSubclassOf(Model::class) -> {
                registerModelAction(
                    method,
                    path,
                    action,
                    middleware,
                    name
                )
            }
            else -> {
                registerScalarAction(
                    method,
                    path,
                    action,
                    middleware,
                    name
                )
            }
        }
    }

    private fun <T : Any, P : Any, R> registerScalarAction(
        method: String,
        path: String,
        action: KFunction2<T, P, R>,
        middleware: List<String> = emptyList(),
        name: String? = null
    ) {
        register(method, path, { params ->
            val controller = resolveController(action)
            val argument = resolveActionParameter(action.parameters[1], params, action.name)
            action.isAccessible = true
            action.call(controller, argument)
        }, middleware, name)
    }

    private fun <T : Any, P : Any, R> registerModelAction(
        method: String,
        path: String,
        action: KFunction2<T, P, R>,
        middleware: List<String> = emptyList(),
        name: String? = null
    ) {
        register(method, path, { params ->
            val controller = resolveController(action)
            val model = resolveBoundModel(action, params)
            action.isAccessible = true
            action.call(controller, model)
        }, middleware, name)
    }

    private fun <T : Any, P1 : Any, P2 : Any, R> registerTwoArgumentControllerAction(
        method: String,
        path: String,
        action: KFunction3<T, P1, P2, R>,
        middleware: List<String> = emptyList(),
        name: String? = null
    ) {
        val firstParameterType = action.parameters[1].type.classifier as? KClass<*>
        val secondParameterType = action.parameters[2].type.classifier as? KClass<*>
        if (
            firstParameterType?.isSubclassOf(Request::class) == true &&
            secondParameterType?.isSubclassOf(Model::class) == true
        ) {
            val strictRequestType = requestTypeOf(action)
                ?: error("No se pudo inferir el tipo de Request para `${action.name}`.")
            register(method, path, { params ->
                val controller = resolveController(action)
                val request = HttpRequestResolver.resolve(strictRequestType)
                val model = resolveBoundModel(action, params)
                action.isAccessible = true
                action.call(controller, request, model)
            }, middleware, name, strictRequestType.java)
            return
        }

        val requestType = requestTypeOf(action)
        register(method, path, { params ->
            val controller = resolveController(action)
            val firstArgument = resolveActionParameter(action.parameters[1], params, action.name)
            val secondArgument = resolveActionParameter(action.parameters[2], params, action.name)
            action.isAccessible = true
            action.call(controller, firstArgument, secondArgument)
        }, middleware, name, requestType?.java)
    }

    private fun <R> registerGenericControllerAction(
        method: String,
        path: String,
        action: KFunction<R>,
        middleware: List<String> = emptyList(),
        name: String? = null
    ) {
        require(action.parameters.size > 3) {
            "`${action.name}` ya esta cubierto por una ruta optimizada; usa el overload especifico."
        }

        val plan = actionPlanCache.computeIfAbsent(action) {
            buildControllerActionPlan(action)
        }

        register(method, path, { params ->
            val controller = resolveController(action)
            val arguments = plan.resolvers.map { resolver -> resolver(params) }.toTypedArray()
            action.isAccessible = true
            action.call(controller, *arguments)
        }, middleware, name, plan.requestType)
    }

    @JvmName("resolveControllerFromKFunction1")
    private fun <T : Any> resolveController(action: KFunction1<T, *>): T {
        return ControllerResolver.resolve(controllerTypeOf(action))
    }

    @JvmName("resolveControllerFromKFunction2")
    private fun <T : Any> resolveController(action: KFunction2<T, *, *>): T {
        return ControllerResolver.resolve(controllerTypeOf(action))
    }

    @JvmName("resolveControllerFromKFunction3")
    private fun <T : Any> resolveController(action: KFunction3<T, *, *, *>): T {
        return ControllerResolver.resolve(controllerTypeOf(action))
    }

    @Suppress("UNCHECKED_CAST")
    @JvmName("resolveControllerFromKFunction")
    private fun <T : Any> resolveController(action: KFunction<*>): T {
        val controllerType = action.parameters.firstOrNull()?.type?.classifier as? KClass<T>
            ?: error("No se pudo inferir el tipo del controlador para `${action.name}`.")
        return ControllerResolver.resolve(controllerType)
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

    @Suppress("UNCHECKED_CAST")
    @JvmName("controllerTypeOfKFunction3")
    private fun <T : Any> controllerTypeOf(action: KFunction3<T, *, *, *>): KClass<T> {
        return action.parameters.first().type.classifier as? KClass<T>
            ?: error("No se pudo inferir el tipo del controlador para `${action.name}`.")
    }

    @Suppress("UNCHECKED_CAST")
    @JvmName("requestTypeOfKFunction2")
    private fun <RQ : Request> requestTypeOf(action: KFunction2<*, RQ, *>): KClass<RQ> {
        val requestType = action.parameters[1].type.classifier as? KClass<RQ>
            ?: error("No se pudo inferir el tipo de Request para `${action.name}`.")

        require(requestType.isSubclassOf(Request::class)) {
            "El segundo parametro de `${action.name}` debe heredar de Request."
        }

        return requestType
    }

    @Suppress("UNCHECKED_CAST")
    @JvmName("requestTypeOfKFunction3")
    private fun requestTypeOf(action: KFunction3<*, *, *, *>): KClass<out Request>? {
        return action.parameters
            .drop(1)
            .mapNotNull { parameter ->
                parameter.type.classifier as? KClass<*>
            }
            .firstOrNull { parameterType ->
                parameterType.isSubclassOf(Request::class)
            } as? KClass<out Request>
    }

    @Suppress("UNCHECKED_CAST")
    @JvmName("resolveBoundModelFromKFunction2")
    private fun resolveBoundModel(
        action: KFunction2<*, *, *>,
        params: Map<String, String>
    ): Model {
        val modelType = action.parameters[1].type.classifier as? KClass<out Model>
            ?: error("No se pudo inferir el tipo del modelo para `${action.name}`.")
        val routeKey = action.parameters[1].name
            ?: modelType.simpleName?.replaceFirstChar { it.lowercase() }
            ?: "id"
        return findBoundModel(modelType, routeKey, params)
    }

    @JvmName("resolveBoundModelFromKFunction3")
    private fun resolveBoundModel(
        action: KFunction3<*, *, *, *>,
        params: Map<String, String>
    ): Model {
        val modelType = action.parameters[2].type.classifier as? KClass<out Model>
            ?: error("No se pudo inferir el tipo del modelo para `${action.name}`.")
        val routeKey = action.parameters[2].name
            ?: modelType.simpleName?.replaceFirstChar { it.lowercase() }
            ?: "id"
        return findBoundModel(modelType, routeKey, params)
    }

    @Suppress("UNCHECKED_CAST")
    private fun findBoundModel(
        modelType: KClass<out Model>,
        routeKey: String,
        params: Map<String, String>
    ): Model {
        val rawValue = params[routeKey]
            ?: params[modelType.simpleName?.replaceFirstChar { it.lowercase() } ?: routeKey]
            ?: throw IllegalArgumentException(
                "No existe el parametro de ruta `$routeKey` para resolver `${modelType.simpleName}`."
            )

        val companion = modelType.companionObjectInstance as? ModelDefinition<out Model>
            ?: error(
                "`${modelType.qualifiedName}` no expone un companion object basado en ModelDefinition para model binding."
            )

        return runBlocking {
            companion.find(rawValue)
        } ?: throw ModelNotFoundException(
            modelName = modelType.simpleName ?: "Model",
            routeKey = routeKey,
            value = rawValue
        )
    }

    private fun resolveActionParameter(
        parameter: KParameter,
        params: Map<String, String>,
        actionName: String
    ): Any {
        val parameterType = parameter.type.classifier as? KClass<*>
            ?: error("No se pudo inferir el tipo del parametro `${parameter.name}` en `${actionName}`.")

        return when {
            parameterType == Map::class -> params
            parameterType.isSubclassOf(Request::class) -> {
                @Suppress("UNCHECKED_CAST")
                HttpRequestResolver.resolve(parameterType as KClass<Request>)
            }
            parameterType.isSubclassOf(Model::class) -> {
                findBoundModel(parameterType as KClass<out Model>, parameter.name ?: "id", params)
            }
            else -> resolveScalarRouteValue(parameter, parameterType, params, actionName)
        }
    }

    private fun resolveScalarRouteValue(
        parameter: KParameter,
        parameterType: KClass<*>,
        params: Map<String, String>,
        actionName: String
    ): Any {
        val routeKey = parameter.name
            ?: if (params.size == 1) params.keys.first() else null
            ?: error(
                "No se pudo inferir el nombre del parametro escalar para `${actionName}`."
            )
        val rawValue = params[routeKey]
            ?: if (params.size == 1) params.values.first() else null
            ?: throw IllegalArgumentException(
                "No existe el parametro de ruta `$routeKey` para resolver `${actionName}`."
            )

        return castScalarRouteValue(rawValue, parameterType, routeKey, actionName)
    }

    private fun castScalarRouteValue(
        rawValue: String,
        parameterType: KClass<*>,
        routeKey: String,
        actionName: String
    ): Any {
        return when (parameterType) {
            String::class -> rawValue
            Int::class -> rawValue.toIntOrNull()
            Long::class -> rawValue.toLongOrNull()
            Double::class -> rawValue.toDoubleOrNull()
            Float::class -> rawValue.toFloatOrNull()
            Boolean::class -> rawValue.toBooleanStrictOrNull()
            Short::class -> rawValue.toShortOrNull()
            Byte::class -> rawValue.toByteOrNull()
            else -> {
                if (parameterType.java.isEnum) {
                    parameterType.java.enumConstants.firstOrNull { candidate ->
                        (candidate as Enum<*>).name.equals(rawValue, ignoreCase = true)
                    }
                } else {
                    null
                }
            }
        } ?: throw IllegalArgumentException(
            "No se pudo convertir `$routeKey=$rawValue` al tipo `${parameterType.simpleName}` en `${actionName}`."
        )
    }

    private fun buildControllerActionPlan(action: KFunction<*>): ControllerActionPlan {
        val requestType = action.parameters
            .drop(1)
            .mapNotNull { parameter ->
                parameter.type.classifier as? KClass<*>
            }
            .firstOrNull { parameterType ->
                parameterType.isSubclassOf(Request::class)
            } as? KClass<out Request>

        val resolvers = action.parameters
            .drop(1)
            .map { parameter ->
                buildActionParameterResolver(parameter, action.name)
            }

        return ControllerActionPlan(
            requestType = requestType?.java,
            resolvers = resolvers
        )
    }

    private fun buildActionParameterResolver(
        parameter: KParameter,
        actionName: String
    ): (Map<String, String>) -> Any {
        val parameterType = parameter.type.classifier as? KClass<*>
            ?: error("No se pudo inferir el tipo del parametro `${parameter.name}` en `${actionName}`.")

        return when {
            parameterType == Map::class -> { params -> params }
            parameterType.isSubclassOf(Request::class) -> {
                @Suppress("UNCHECKED_CAST")
                { _ -> HttpRequestResolver.resolve(parameterType as KClass<Request>) }
            }
            parameterType.isSubclassOf(Model::class) -> {
                val routeKey = parameter.name ?: "id"
                @Suppress("UNCHECKED_CAST")
                { params -> findBoundModel(parameterType as KClass<out Model>, routeKey, params) }
            }
            else -> {
                { params -> resolveScalarRouteValue(parameter, parameterType, params, actionName) }
            }
        }
    }

    private fun currentGroupContext(): GroupContext {
        val stack = currentGroupStack.get()
        if (stack.isEmpty()) {
            return GroupContext()
        }

        var prefix = ""
        val middleware = linkedSetOf<String>()
        var namePrefix = ""

        stack.forEach { context ->
            prefix = joinPathSegments(prefix, context.prefix)
            context.middleware.forEach(middleware::add)
            namePrefix += context.namePrefix
        }

        return GroupContext(
            prefix = prefix,
            middleware = middleware.toList(),
            namePrefix = namePrefix
        )
    }

    private fun mergeMiddleware(
        inherited: List<String>,
        route: List<String>
    ): List<String> {
        val aliases = linkedSetOf<String>()
        inherited.forEach(aliases::add)
        route.forEach(aliases::add)
        return aliases.toList()
    }

    private fun joinPathSegments(left: String, right: String): String {
        val normalizedLeft = left.trim().trim('/')
        val normalizedRight = right.trim().trim('/')

        return when {
            normalizedLeft.isBlank() && normalizedRight.isBlank() -> ""
            normalizedLeft.isBlank() -> normalizedRight
            normalizedRight.isBlank() -> normalizedLeft
            else -> "$normalizedLeft/$normalizedRight"
        }
    }
}

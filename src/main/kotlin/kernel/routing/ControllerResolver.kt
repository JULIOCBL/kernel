package kernel.routing

import kernel.foundation.ApplicationRuntime
import kotlin.reflect.KClass
import kotlin.reflect.jvm.isAccessible

internal object ControllerResolver {
    fun <T : Any> resolve(type: KClass<T>): T {
        val app = ApplicationRuntime.current()
        val registry = app.config.get("services.routes.controllers") as? ControllerRegistry
        val resolved = registry?.resolve(type, app)

        if (resolved != null) {
            @Suppress("UNCHECKED_CAST")
            return resolved as T
        }

        val constructor = type.constructors.firstOrNull { it.parameters.isEmpty() }
            ?: error(
                "No se pudo resolver `${type.qualifiedName}`. " +
                    "Registra el controlador en Route.registerController(...) o agrega constructor vacío."
            )

        constructor.isAccessible = true
        return constructor.call()
    }
}

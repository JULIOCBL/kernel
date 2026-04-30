package kernel.routing

import kernel.foundation.Application
import kotlin.reflect.KClass

/**
 * Registro minimo de controladores para resolver referencias de método.
 *
 * Si un controlador no está registrado aquí, el kernel intenta construirlo con
 * un constructor vacío como fallback.
 */
class ControllerRegistry {
    private val factories = linkedMapOf<KClass<*>, (Application) -> Any>()

    fun <T : Any> register(type: KClass<T>, factory: (Application) -> T) {
        factories[type] = factory as (Application) -> Any
    }

    fun resolve(type: KClass<*>, application: Application): Any? {
        return factories[type]?.invoke(application)
    }
}

package kernel.providers

import kernel.foundation.Application
import kotlin.reflect.KClass

/**
 * Factory declarativo para construir providers sin perder su tipo real.
 *
 * Esto permite que `Application.registerAll(...)` detecte duplicados antes de
 * instanciar el provider, evitando trabajo o efectos secundarios innecesarios
 * en providers repetidos por accidente dentro del bootstrap.
 */
class ProviderFactory constructor(
    val type: KClass<out ServiceProvider>,
    private val creator: (Application) -> ServiceProvider
) {
    fun create(application: Application): ServiceProvider {
        return creator(application)
    }

    operator fun invoke(application: Application): ServiceProvider {
        return create(application)
    }
}

inline fun <reified T : ServiceProvider> providerFactory(
    noinline creator: (Application) -> T
): ProviderFactory {
    return ProviderFactory(T::class, creator)
}

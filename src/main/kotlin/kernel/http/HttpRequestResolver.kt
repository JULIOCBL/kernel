package kernel.http

import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.jvm.isAccessible

internal object HttpRequestResolver {
    @Suppress("UNCHECKED_CAST")
    fun <T : Request> resolve(type: KClass<T>): T {
        val request = HttpRequestRuntime.current()

        if (type == Request::class) {
            return request as T
        }

        val constructor = type.constructors.firstOrNull { constructor ->
            constructor.parameters.size == 1 &&
                constructor.parameters.first().type.classifier == Request::class
        } ?: error(
            "No se pudo resolver `${type.qualifiedName}`. " +
                "Las Request HTTP deben exponer un constructor que reciba `Request`."
        )

        constructor.isAccessible = true
        val resolved = constructor.call(request)
        if (resolved is FormRequest) {
            resolved.validateResolved()
        }

        return resolved
    }
}

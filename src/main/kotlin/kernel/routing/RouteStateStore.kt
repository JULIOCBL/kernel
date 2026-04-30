package kernel.routing

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Estado observable minimo para que una app desktop pueda reaccionar a
 * navegacion inicial o a deep links recibidos mientras ya estaba abierta.
 */
class RouteStateStore<T>(initialValue: T? = null) {
    private val listeners = CopyOnWriteArrayList<(T?) -> Unit>()

    @Volatile
    private var currentValue: T? = initialValue

    fun current(): T? = currentValue

    fun update(value: T?) {
        currentValue = value
        listeners.forEach { listener -> listener(value) }
    }

    fun subscribe(listener: (T?) -> Unit): AutoCloseable {
        listeners += listener
        listener(currentValue)

        return AutoCloseable {
            listeners.remove(listener)
        }
    }
}

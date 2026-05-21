package kernel.foundation

import java.util.concurrent.CancellationException
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Vista derivada y de solo lectura sobre un `StateFlow` fuente.
 *
 * Permite exponer slices del estado sin duplicar almacenamiento mutable ni
 * romper la semantica reactiva del flujo original.
 */
internal class DerivedStateFlow<T, R>(
    private val source: StateFlow<T>,
    private val transform: (T) -> R
) : StateFlow<R> {
    override val value: R
        get() = transform(source.value)

    override val replayCache: List<R>
        get() = listOf(value)

    override suspend fun collect(collector: FlowCollector<R>): Nothing {
        source
            .map(transform)
            .distinctUntilChanged()
            .collect { value -> collector.emit(value) }

        throw CancellationException("DerivedStateFlow no deberia completar.")
    }
}

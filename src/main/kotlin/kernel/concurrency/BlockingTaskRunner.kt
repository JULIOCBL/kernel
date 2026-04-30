package kernel.concurrency

import java.util.concurrent.Future

/**
 * Ejecuta trabajo bloqueante fuera del hilo de UI o del hilo llamador.
 *
 * El contrato no asume paralelismo interno del dominio; solo ofrece un lugar
 * controlado para delegar JDBC, filesystem u otras operaciones bloqueantes.
 */
interface BlockingTaskRunner : AutoCloseable {
    fun <T> submit(task: () -> T): Future<T>

    fun <T> run(task: () -> T): T {
        return submit(task).get()
    }

    override fun close() {
    }
}

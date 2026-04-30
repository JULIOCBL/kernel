package kernel.providers

import kernel.concurrency.BlockingTaskRunner
import kernel.concurrency.JvmBlockingTaskRunner

/**
 * Registra un runner compartido para trabajo bloqueante.
 *
 * Pensado para desktop: JDBC, migraciones al arranque y otras tareas largas
 * pueden ejecutarse fuera del hilo de UI sin cambiar su lógica interna.
 */
open class BlockingTaskServiceProvider(app: kernel.foundation.Application) : ServiceProvider(app) {
    override fun register() {
        val existing = app.config.get("services.tasks.blocking") as? BlockingTaskRunner
        if (existing != null) {
            return
        }

        val virtualThreads = app.config.bool("app.threads.virtual.enabled", true)
        val namePrefix = app.config.string("app.threads.blocking.namePrefix", "kernel-blocking")

        app.config.set(
            "services.tasks.blocking",
            JvmBlockingTaskRunner(
                virtualThreads = virtualThreads,
                namePrefix = namePrefix
            )
        )
    }
}

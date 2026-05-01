package kernel.database

import kernel.foundation.Application

private const val DATABASE_MANAGER_CACHE_KEY = "services.database.manager"
private const val DATABASE_SHUTDOWN_HOOK_CACHE_KEY = "services.database.manager.shutdownHook"

/**
 * Cierra explicitamente el `DatabaseManager` si ya fue materializado dentro de
 * la aplicacion actual.
 *
 * No fuerza la construccion del manager; simplemente libera los pools activos
 * si la app ya habia usado conexiones durante su lifecycle.
 */
fun Application.closeDatabaseManager(): Application {
    (config.get(DATABASE_MANAGER_CACHE_KEY) as? AutoCloseable)?.close()
    return this
}

/**
 * Registra un shutdown hook idempotente para cerrar el `DatabaseManager`
 * cuando el proceso termine.
 *
 * El hook no crea el manager por adelantado: solo intenta cerrarlo si ya fue
 * cacheado previamente en la aplicacion.
 */
fun Application.installDatabaseShutdownHook(): Application {
    synchronized(this) {
        val existing = config.get(DATABASE_SHUTDOWN_HOOK_CACHE_KEY) as? DatabaseShutdownHook
        if (existing != null) {
            existing.register()
            return this
        }

        val registration = DatabaseShutdownHook(this).also(DatabaseShutdownHook::register)
        config.set(DATABASE_SHUTDOWN_HOOK_CACHE_KEY, registration)
    }

    return this
}

internal class DatabaseShutdownHook(
    private val application: Application,
    private val registerHook: (Thread) -> Unit = Runtime.getRuntime()::addShutdownHook,
    private val removeHook: (Thread) -> Unit = Runtime.getRuntime()::removeShutdownHook
) : AutoCloseable {
    private val thread = Thread(
        { application.closeDatabaseManager() },
        "kernel-database-shutdown"
    )

    @Volatile
    private var registered: Boolean = false

    fun register(): DatabaseShutdownHook {
        if (!registered) {
            registerHook(thread)
            registered = true
        }

        return this
    }

    override fun close() {
        if (!registered) {
            return
        }

        runCatching {
            removeHook(thread)
        }

        registered = false
    }
}

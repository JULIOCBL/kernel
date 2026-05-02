package kernel.database.pdo.connections

import com.zaxxer.hikari.HikariDataSource
import kernel.config.ConfigStore
import kernel.database.pdo.drivers.DatabaseDrivers
import kernel.foundation.Application
import java.sql.Connection
import java.util.concurrent.ConcurrentHashMap

/**
 * Punto de acceso para conexiones nombradas al estilo Laravel.
 *
 * El manager crea pools Hikari solo cuando una conexion concreta se usa por
 * primera vez desde `connect()` o `withConnection()`.
 *
 * Nota de lifecycle:
 * el kernel de la aplicacion deberia registrar un shutdown hook que invoque
 * `close()` para liberar todos los pools activos antes de terminar el proceso.
 */
class DatabaseManager private constructor(
    private val configuration: DatabaseConnectionsConfig
) : ConnectionResolver, AutoCloseable {
    private val activePools = ConcurrentHashMap<String, HikariDataSource>()

    init {
        val unsupportedEngines = configuration.connections.values
            .map { it.driver.id }
            .filterNot { it in ALLOWED_ENGINES }
            .distinct()

        require(unsupportedEngines.isEmpty()) {
            "Motor de base de datos no soportado: ${unsupportedEngines.joinToString(", ")}. " +
                "Soportados hoy: ${ALLOWED_ENGINES.joinToString(", ")}."
        }
    }

    override fun defaultConnectionName(): String = configuration.defaultConnection

    fun connectionNames(): List<String> = configuration.connections.keys.toList()

    fun hasConnection(name: String): Boolean = configuration.connections.containsKey(name)

    override fun connectionConfig(name: String?): DatabaseConnectionConfig {
        val targetName = name?.trim().takeUnless { it.isNullOrEmpty() }
            ?: configuration.defaultConnection

        return configuration.connections[targetName]
            ?: throw IllegalArgumentException("La conexion de base de datos `$targetName` no existe.")
    }

    fun connect(name: String? = null): Connection {
        val targetConfig = connectionConfig(name)

        return if (targetConfig.pool.enabled) {
            poolFor(targetConfig).connection
        } else {
            targetConfig.open()
        }
    }

    override fun connection(name: String?): Connection {
        return connect(name)
    }

    inline fun <T> withConnection(
        name: String? = null,
        block: (Connection) -> T
    ): T {
        return connect(name).use(block)
    }

    fun resolvedConnectionProperties(name: String? = null): Map<String, String> {
        val targetConfig = connectionConfig(name)
        return targetConfig.resolvedProperties()
    }

    internal fun activePooledConnectionNames(): Set<String> = activePools.keys.toSet()

    override fun close() {
        activePools.values.forEach(HikariDataSource::close)
        activePools.clear()
    }

    private fun poolFor(config: DatabaseConnectionConfig): HikariDataSource {
        return activePools.computeIfAbsent(config.name) {
            config.createPooledDataSource()
        }
    }

    companion object {
        private const val APPLICATION_CACHE_KEY = "services.database.manager"
        private val ALLOWED_ENGINES = setOf(
            DatabaseDrivers.resolve("pgsql").id,
            DatabaseDrivers.resolve("mariadb").id
        )

        fun from(application: Application): DatabaseManager {
            synchronized(application) {
                val existing = application.config.get(APPLICATION_CACHE_KEY) as? DatabaseManager
                if (existing != null) {
                    return existing
                }

                return from(application.config).also { manager ->
                    application.config.set(APPLICATION_CACHE_KEY, manager)
                }
            }
        }

        fun from(config: ConfigStore): DatabaseManager {
            return DatabaseManager(
                configuration = DatabaseConfigResolver.resolve(config)
            )
        }
    }
}

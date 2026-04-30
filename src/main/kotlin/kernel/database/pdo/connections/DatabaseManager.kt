package kernel.database.pdo.connections

import kernel.foundation.Application
import java.sql.Connection

/**
 * Punto de acceso para conexiones nombradas al estilo Laravel.
 */
class DatabaseManager private constructor(
    private val configuration: DatabaseConnectionsConfig
) : ConnectionResolver, AutoCloseable {
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
        return connectionConfig(name).open()
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

    override fun close() {
        configuration.connections.values.forEach(DatabaseConnectionConfig::close)
    }

    companion object {
        fun from(application: Application): DatabaseManager {
            val existing = application.config.get("services.database.manager") as? DatabaseManager
            if (existing != null) {
                return existing
            }

            return from(application.config).also { manager ->
                application.config.set("services.database.manager", manager)
            }
        }

        fun from(config: kernel.config.ConfigStore): DatabaseManager {
            return DatabaseManager(DatabaseConfigResolver.resolve(config))
        }
    }
}

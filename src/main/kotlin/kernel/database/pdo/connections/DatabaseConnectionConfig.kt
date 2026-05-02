package kernel.database.pdo.connections

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kernel.database.pdo.drivers.DatabaseDriver
import java.sql.Connection
import java.sql.DriverManager
import java.util.Properties

/**
 * Configuracion materializada de una conexion de base de datos.
 */
data class DatabaseConnectionConfig(
    val name: String,
    val driver: DatabaseDriver,
    val url: String,
    val jdbcDriverClass: String? = null,
    val username: String? = null,
    val password: String? = null,
    val properties: Map<String, String> = emptyMap(),
    val pool: DatabasePoolConfig = DatabasePoolConfig()
) {
    fun supportsSchemaMigrations(): Boolean = driver.supportsSchemaMigrations

    fun supportsSchemaTransactions(): Boolean = driver.supportsSchemaTransactions

    fun requireSchemaMigrationSupport() {
        require(supportsSchemaMigrations()) {
            "La conexion `$name` usa `${driver.id}`, pero el kernel solo soporta migraciones/schema para: " +
                kernel.database.pdo.drivers.DatabaseDrivers.supportedIds().joinToString(", ")
        }
    }

    fun open(extraProperties: Map<String, String> = emptyMap()): Connection {
        loadDriverIfNeeded()

        val connectionProperties = toConnectionProperties(extraProperties)

        return if (connectionProperties.isEmpty()) {
            DriverManager.getConnection(url)
        } else {
            DriverManager.getConnection(url, connectionProperties)
        }
    }

    internal fun createPooledDataSource(extraProperties: Map<String, String> = emptyMap()): HikariDataSource {
        loadDriverIfNeeded()
        val connectionProperties = toConnectionProperties(extraProperties)

        val hikari = HikariConfig().apply {
            poolName = "kernel-$name"
            jdbcUrl = url
            driverClassName = resolvedJdbcDriverClass()
            username = this@DatabaseConnectionConfig.username
            password = this@DatabaseConnectionConfig.password
            minimumIdle = pool.minimumIdle
            maximumPoolSize = pool.maximumPoolSize
            idleTimeout = pool.idleTimeoutMs
            connectionTimeout = pool.connectionTimeoutMs
            maxLifetime = pool.maxLifetimeMs
            validationTimeout = pool.validationTimeoutMs
            if (pool.keepAliveTimeMs > 0) {
                keepaliveTime = pool.keepAliveTimeMs
            }
            connectionProperties.forEach { key, value ->
                addDataSourceProperty(key.toString(), value)
            }
        }

        return HikariDataSource(hikari)
    }

    private fun loadDriverIfNeeded() {
        Class.forName(resolvedJdbcDriverClass())
    }

    private fun resolvedJdbcDriverClass(): String {
        return jdbcDriverClass?.trim()?.takeIf(String::isNotEmpty)
            ?: driver.defaultJdbcDriverClass
    }

    fun resolvedProperties(extraProperties: Map<String, String> = emptyMap()): Map<String, String> {
        val resolved = linkedMapOf<String, String>()

        resolved.putAll(properties)
        resolved.putAll(extraProperties)

        if (!username.isNullOrBlank()) {
            resolved["user"] = username
        }

        if (!password.isNullOrBlank()) {
            resolved["password"] = password
        }

        return resolved.toMap()
    }

    private fun toConnectionProperties(extraProperties: Map<String, String>): Properties {
        val connectionProperties = Properties()
        resolvedProperties(extraProperties).forEach(connectionProperties::setProperty)

        return connectionProperties
    }
}

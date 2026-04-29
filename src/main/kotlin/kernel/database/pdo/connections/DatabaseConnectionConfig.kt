package kernel.database.pdo.connections

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
    val properties: Map<String, String> = emptyMap()
) {
    fun supportsSchemaMigrations(): Boolean = driver.supportsSchemaMigrations

    fun supportsSchemaTransactions(): Boolean = driver.supportsSchemaTransactions

    fun requireSchemaMigrationSupport() {
        require(supportsSchemaMigrations()) {
            "La conexion `$name` usa `${driver.id}`, pero el kernel solo soporta migraciones/schema para: " +
                kernel.database.pdo.drivers.DatabaseDrivers.supportedIds().joinToString(", ")
        }
    }

    fun open(): Connection {
        loadDriverIfNeeded()

        val connectionProperties = Properties()
        properties.forEach(connectionProperties::setProperty)

        if (!username.isNullOrBlank()) {
            connectionProperties.setProperty("user", username)
        }

        if (!password.isNullOrBlank()) {
            connectionProperties.setProperty("password", password)
        }

        return if (connectionProperties.isEmpty()) {
            DriverManager.getConnection(url)
        } else {
            DriverManager.getConnection(url, connectionProperties)
        }
    }

    private fun loadDriverIfNeeded() {
        Class.forName(resolvedJdbcDriverClass())
    }

    private fun resolvedJdbcDriverClass(): String {
        return jdbcDriverClass?.trim()?.takeIf(String::isNotEmpty)
            ?: driver.defaultJdbcDriverClass
    }
}

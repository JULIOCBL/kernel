package kernel.database.pdo.connections

import kernel.config.ConfigStore
import kernel.database.pdo.drivers.DatabaseDrivers

/**
 * Materializa la seccion `database.*` del `ConfigStore` en una estructura
 * tipada y valida.
 */
object DatabaseConfigResolver {
    fun resolve(config: ConfigStore): DatabaseConnectionsConfig {
        val defaultConnection = config.string("database.default").trim()
        require(defaultConnection.isNotEmpty()) {
            "La configuracion `database.default` es obligatoria."
        }

        val rawConnections = config.map("database.connections")
        require(rawConnections.isNotEmpty()) {
            "La configuracion `database.connections` debe definir al menos una conexion."
        }

        val resolvedConnections = linkedMapOf<String, DatabaseConnectionConfig>()

        for ((name, value) in rawConnections) {
            val connectionName = name.trim()
            require(connectionName.isNotEmpty()) {
                "Cada conexion de base de datos debe tener un nombre valido."
            }

            val rawConnection = value as? Map<*, *>
                ?: throw IllegalArgumentException(
                    "La conexion `database.connections.$connectionName` debe ser un mapa."
                )

            resolvedConnections[connectionName] = resolveConnection(connectionName, rawConnection)
        }

        require(resolvedConnections.containsKey(defaultConnection)) {
            "La conexion por defecto `$defaultConnection` no existe en `database.connections`."
        }

        return DatabaseConnectionsConfig(
            defaultConnection = defaultConnection,
            connections = resolvedConnections.toMap()
        )
    }

    private fun resolveConnection(
        connectionName: String,
        rawConnection: Map<*, *>
    ): DatabaseConnectionConfig {
        val driverId = stringValue(rawConnection["driver"])
        require(driverId.isNotEmpty()) {
            "La conexion `database.connections.$connectionName.driver` es obligatoria."
        }

        val url = stringValue(rawConnection["url"])
        require(url.isNotEmpty()) {
            "La conexion `database.connections.$connectionName.url` es obligatoria."
        }

        val driver = DatabaseDrivers.resolve(driverId)
        val jdbcDriverClass = stringValue(rawConnection["jdbcDriver"]).ifEmpty { null }
        val username = stringValue(rawConnection["username"]).ifEmpty { null }
        val password = stringValue(rawConnection["password"]).ifEmpty { null }
        val properties = stringMap(
            value = rawConnection["properties"],
            keyPath = "database.connections.$connectionName.properties"
        )

        return DatabaseConnectionConfig(
            name = connectionName,
            driver = driver,
            url = url,
            jdbcDriverClass = jdbcDriverClass,
            username = username,
            password = password,
            properties = properties
        )
    }

    private fun stringValue(value: Any?): String {
        return when (value) {
            null -> ""
            is String -> value.trim()
            else -> value.toString().trim()
        }
    }

    private fun stringMap(value: Any?, keyPath: String): Map<String, String> {
        if (value == null) {
            return emptyMap()
        }

        val rawMap = value as? Map<*, *>
            ?: throw IllegalArgumentException("La configuracion `$keyPath` debe ser un mapa.")

        val normalized = linkedMapOf<String, String>()

        for ((rawKey, rawValue) in rawMap) {
            val key = rawKey?.toString()?.trim().orEmpty()

            require(key.isNotEmpty()) {
                "La configuracion `$keyPath` contiene una propiedad sin nombre valido."
            }

            normalized[key] = rawValue?.toString()?.trim().orEmpty()
        }

        return normalized.toMap()
    }
}

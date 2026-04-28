package kernel.database.pdo.connections

/**
 * Conjunto de conexiones disponibles para la aplicacion actual.
 */
data class DatabaseConnectionsConfig(
    val defaultConnection: String,
    val connections: Map<String, DatabaseConnectionConfig>
)

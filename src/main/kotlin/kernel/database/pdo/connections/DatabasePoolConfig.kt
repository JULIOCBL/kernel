package kernel.database.pdo.connections

/**
 * Configuracion optativa del pool JDBC.
 */
data class DatabasePoolConfig(
    val enabled: Boolean = true,
    val minimumIdle: Int = 1,
    val maximumPoolSize: Int = 10,
    val idleTimeoutMs: Long = 120_000,
    val connectionTimeoutMs: Long = 30_000,
    val maxLifetimeMs: Long = 600_000,
    val keepAliveTimeMs: Long = 0,
    val validationTimeoutMs: Long = 5_000
)

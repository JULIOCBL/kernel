package kernel.database.migrations

/**
 * Registro persistido de una migracion ya ejecutada.
 */
data class MigrationRecord(
    val migration: String,
    val batch: Int
)

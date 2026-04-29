package kernel.database.migrations

/**
 * Estado visible de una migracion dentro del registry de la app.
 */
data class MigrationStatus(
    val migration: String,
    val status: MigrationState,
    val batch: Int? = null,
    val connection: String
)

enum class MigrationState {
    RAN,
    PENDING
}

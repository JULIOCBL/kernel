package kernel.database.migrations

/**
 * Plantillas disponibles para generar el codigo base de una migracion.
 */
enum class MigrationStubTemplate {
    BLANK,
    CREATE_TABLE,
    UPDATE_TABLE,
    DROP_TABLE;

    val stubFileName: String
        get() = when (this) {
            BLANK -> "blank.stub"
            CREATE_TABLE -> "create_table.stub"
            UPDATE_TABLE -> "update_table.stub"
            DROP_TABLE -> "drop_table.stub"
        }
}

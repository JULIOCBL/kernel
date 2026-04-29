package kernel.database.schema

/**
 * Representacion validada de una tabla lista para renderizarse como SQL.
 */
internal data class TableDefinition(
    val name: String,
    val columns: List<ColumnDefinition>,
    val primaryKey: PrimaryKeyDefinition?,
    val constraints: List<TableConstraintDefinition>
)

package kernel.database.statements

import kernel.database.schema.ColumnDefinition

/**
 * Renderiza `ALTER TABLE ... ADD COLUMN`.
 */
internal data class AddColumnStatement(
    private val table: String,
    private val column: ColumnDefinition,
    private val ifNotExists: Boolean
) : SqlStatement {
    /**
     * Convierte la operacion a SQL PostgreSQL.
     */
    override fun toSql(): String {
        val existenceClause = if (ifNotExists) {
            " IF NOT EXISTS"
        } else {
            ""
        }

        return "ALTER TABLE $table ADD COLUMN$existenceClause ${column.toSql()};"
    }
}

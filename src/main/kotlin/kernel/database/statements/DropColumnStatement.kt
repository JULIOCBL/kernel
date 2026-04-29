package kernel.database.statements

/**
 * Renderiza `ALTER TABLE ... DROP COLUMN`.
 */
internal data class DropColumnStatement(
    private val table: String,
    private val column: String,
    private val ifExists: Boolean,
    private val cascade: Boolean
) : SqlStatement {
    /**
     * Convierte la operacion a SQL PostgreSQL.
     */
    override fun toSql(): String {
        val existenceClause = if (ifExists) {
            " IF EXISTS"
        } else {
            ""
        }
        val cascadeClause = if (cascade) {
            " CASCADE"
        } else {
            ""
        }

        return "ALTER TABLE $table DROP COLUMN$existenceClause $column$cascadeClause;"
    }
}

package kernel.database.migrations.statements

/**
 * Renderiza `ALTER TABLE ... DROP CONSTRAINT`.
 */
internal data class DropConstraintStatement(
    private val table: String,
    private val name: String,
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

        return "ALTER TABLE $table DROP CONSTRAINT$existenceClause $name$cascadeClause;"
    }
}

package kernel.database.migrations.statements

/**
 * Renderiza `DROP SCHEMA`.
 */
internal data class DropSchemaStatement(
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

        return "DROP SCHEMA$existenceClause $name$cascadeClause;"
    }
}

package kernel.database.migrations.statements

/**
 * Renderiza `DROP EXTENSION`.
 */
internal data class DropExtensionStatement(
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

        return "DROP EXTENSION$existenceClause $name$cascadeClause;"
    }
}

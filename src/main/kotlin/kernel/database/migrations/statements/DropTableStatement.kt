package kernel.database.migrations.statements

/**
 * Renderiza `DROP TABLE`.
 */
internal data class DropTableStatement(
    private val name: String,
    private val ifExists: Boolean
) : SqlStatement {
    /**
     * Convierte la operacion a SQL PostgreSQL.
     */
    override fun toSql(): String {
        val prefix = if (ifExists) {
            "DROP TABLE IF EXISTS"
        } else {
            "DROP TABLE"
        }

        return "$prefix $name;"
    }
}

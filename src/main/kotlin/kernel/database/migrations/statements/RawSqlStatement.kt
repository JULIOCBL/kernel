package kernel.database.migrations.statements

/**
 * Conserva una sentencia SQL escrita manualmente.
 */
internal data class RawSqlStatement(
    private val sql: String
) : SqlStatement {
    init {
        require(sql.isNotBlank()) { "La sentencia SQL no puede estar vacia." }
    }

    /**
     * Convierte la operacion a SQL PostgreSQL.
     */
    override fun toSql(): String {
        return sql.trim()
    }
}

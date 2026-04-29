package kernel.database.statements

/**
 * Renderiza `CREATE TRIGGER`.
 */
internal data class CreateTriggerStatement(
    private val name: String,
    private val table: String,
    private val timing: String,
    private val events: List<String>,
    private val function: String,
    private val forEach: String,
    private val whenExpression: String?
) : SqlStatement {
    /**
     * Convierte la operacion a SQL PostgreSQL.
     */
    override fun toSql(): String {
        val whenClause = whenExpression?.let { expression -> " WHEN ($expression)" }.orEmpty()

        return "CREATE TRIGGER $name\n$timing ${events.joinToString(" OR ")} ON $table\nFOR EACH $forEach$whenClause\nEXECUTE FUNCTION $function;"
    }
}

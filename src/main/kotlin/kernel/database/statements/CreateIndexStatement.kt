package kernel.database.statements

/**
 * Renderiza `CREATE INDEX`, incluyendo variantes unique, concurrently e indices parciales.
 */
internal data class CreateIndexStatement(
    private val name: String,
    private val table: String,
    private val columns: List<String>,
    private val unique: Boolean,
    private val ifNotExists: Boolean,
    private val concurrently: Boolean,
    private val using: String? = null,
    private val include: List<String> = emptyList(),
    private val where: String? = null
) : SqlStatement {
    /**
     * Convierte la operacion a SQL PostgreSQL.
     */
    override fun toSql(): String {
        val uniqueClause = if (unique) {
            "UNIQUE "
        } else {
            ""
        }
        val concurrentlyClause = if (concurrently) {
            "CONCURRENTLY "
        } else {
            ""
        }
        val existenceClause = if (ifNotExists) {
            "IF NOT EXISTS "
        } else {
            ""
        }
        val usingClause = using?.let { method -> "USING $method " }.orEmpty()
        val includeClause = include.takeIf { columns -> columns.isNotEmpty() }
            ?.joinToString(", ", prefix = " INCLUDE (", postfix = ")")
            .orEmpty()
        val whereClause = where?.let { expression -> " WHERE $expression" }.orEmpty()

        return "CREATE ${uniqueClause}INDEX ${concurrentlyClause}${existenceClause}$name ON $table ${usingClause}(${columns.joinToString(", ")})$includeClause$whereClause;"
    }
}

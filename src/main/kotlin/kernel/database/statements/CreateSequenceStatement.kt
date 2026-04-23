package kernel.database.migrations.statements

/**
 * Renderiza `CREATE SEQUENCE`.
 */
internal data class CreateSequenceStatement(
    private val name: String,
    private val ifNotExists: Boolean,
    private val incrementBy: Long?,
    private val minValue: Long?,
    private val maxValue: Long?,
    private val startWith: Long?,
    private val cache: Long?,
    private val cycle: Boolean,
    private val ownedBy: String?
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
        val options = listOfNotNull(
            incrementBy?.let { value -> "INCREMENT BY $value" },
            minValue?.let { value -> "MINVALUE $value" },
            maxValue?.let { value -> "MAXVALUE $value" },
            startWith?.let { value -> "START WITH $value" },
            cache?.let { value -> "CACHE $value" },
            if (cycle) "CYCLE" else null,
            ownedBy?.let { value -> "OWNED BY $value" }
        )
        val optionsClause = options.takeIf { values -> values.isNotEmpty() }
            ?.joinToString(" ", prefix = " ")
            .orEmpty()

        return "CREATE SEQUENCE$existenceClause $name$optionsClause;"
    }
}

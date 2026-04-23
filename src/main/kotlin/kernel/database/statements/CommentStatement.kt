package kernel.database.migrations.statements

import kernel.database.migrations.support.SqlLiteral

/**
 * Renderiza `COMMENT ON ... IS ...`.
 */
internal data class CommentStatement(
    private val target: String,
    private val comment: String?
) : SqlStatement {
    /**
     * Convierte la operacion a SQL PostgreSQL.
     */
    override fun toSql(): String {
        val value = comment?.let { text -> SqlLiteral.string(text) } ?: "NULL"

        return "COMMENT ON $target IS $value;"
    }
}

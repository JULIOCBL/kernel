package kernel.database.migrations.schema

import kernel.database.migrations.support.SqlIdentifier

/**
 * Builder fluido para constraints `FOREIGN KEY`.
 */
class ForeignKeyDefinition internal constructor(
    private val name: String,
    private val columns: List<String>
) : TableConstraintDefinition {
    private var referencedTable: String? = null
    private var referencedColumns: List<String> = listOf("id")
    private var onDeleteAction: String? = null
    private var onUpdateAction: String? = null

    /**
     * Define las columnas de la tabla referenciada.
     */
    fun references(vararg columns: String): ForeignKeyDefinition {
        require(columns.isNotEmpty()) {
            "Debes indicar al menos una columna referenciada."
        }

        referencedColumns = columns.map { column ->
            SqlIdentifier.requireValid(column, "Columna referenciada")
        }

        return this
    }

    /**
     * Define la tabla referenciada por la foreign key.
     */
    fun on(table: String): ForeignKeyDefinition {
        referencedTable = SqlIdentifier.requireQualified(table, "Tabla referenciada")

        return this
    }

    /**
     * Atajo estilo Laravel para referenciar una tabla y columna.
     */
    fun constrained(table: String, column: String = "id"): ForeignKeyDefinition {
        return references(column).on(table)
    }

    /**
     * Define la accion `ON DELETE`.
     */
    fun onDelete(action: String): ForeignKeyDefinition {
        onDeleteAction = referentialAction(action)

        return this
    }

    /**
     * Define la accion `ON UPDATE`.
     */
    fun onUpdate(action: String): ForeignKeyDefinition {
        onUpdateAction = referentialAction(action)

        return this
    }

    /**
     * Usa `ON DELETE CASCADE`.
     */
    fun cascadeOnDelete(): ForeignKeyDefinition = onDelete("CASCADE")

    /**
     * Usa `ON DELETE RESTRICT`.
     */
    fun restrictOnDelete(): ForeignKeyDefinition = onDelete("RESTRICT")

    /**
     * Usa `ON DELETE SET NULL`.
     */
    fun nullOnDelete(): ForeignKeyDefinition = onDelete("SET NULL")

    /**
     * Usa `ON DELETE SET DEFAULT`.
     */
    fun defaultOnDelete(): ForeignKeyDefinition = onDelete("SET DEFAULT")

    /**
     * Usa `ON DELETE NO ACTION`.
     */
    fun noActionOnDelete(): ForeignKeyDefinition = onDelete("NO ACTION")

    /**
     * Usa `ON UPDATE CASCADE`.
     */
    fun cascadeOnUpdate(): ForeignKeyDefinition = onUpdate("CASCADE")

    /**
     * Usa `ON UPDATE RESTRICT`.
     */
    fun restrictOnUpdate(): ForeignKeyDefinition = onUpdate("RESTRICT")

    /**
     * Usa `ON UPDATE SET NULL`.
     */
    fun nullOnUpdate(): ForeignKeyDefinition = onUpdate("SET NULL")

    /**
     * Usa `ON UPDATE SET DEFAULT`.
     */
    fun defaultOnUpdate(): ForeignKeyDefinition = onUpdate("SET DEFAULT")

    /**
     * Usa `ON UPDATE NO ACTION`.
     */
    fun noActionOnUpdate(): ForeignKeyDefinition = onUpdate("NO ACTION")

    /**
     * Renderiza la constraint completa para `CREATE TABLE` o `ALTER TABLE`.
     */
    override fun toSql(): String {
        val table = referencedTable
            ?: error("La foreign key '$name' debe indicar tabla referenciada con on(...) o constrained(...).")
        val actions = listOfNotNull(
            onDeleteAction?.let { action -> "ON DELETE $action" },
            onUpdateAction?.let { action -> "ON UPDATE $action" }
        )

        return listOf(
            "CONSTRAINT $name FOREIGN KEY (${columns.joinToString(", ")})",
            "REFERENCES $table (${referencedColumns.joinToString(", ")})"
        ).plus(actions).joinToString(" ")
    }

    /**
     * Valida y normaliza acciones referenciales soportadas por PostgreSQL.
     */
    private fun referentialAction(action: String): String {
        val normalizedAction = action.trim().uppercase()

        require(
            normalizedAction in setOf("CASCADE", "RESTRICT", "SET NULL", "SET DEFAULT", "NO ACTION")
        ) {
            "Accion referencial no soportada: $action."
        }

        return normalizedAction
    }
}

package kernel.database.schema

import kernel.database.support.SqlIdentifier

/**
 * Builder usado para declarar una sola columna en `ALTER TABLE ... ADD COLUMN`.
 */
class ColumnBlueprint internal constructor() : SchemaColumnBlueprint() {
    private var column: ColumnDefinition? = null

    /**
     * Ejecuta el bloque de declaracion y asegura que devuelva la columna creada.
     */
    internal fun build(definition: ColumnBlueprint.() -> ColumnDefinition): ColumnDefinition {
        val returnedColumn = definition()
        val createdColumn = column
            ?: error("Debes declarar una columna para agregarla.")

        require(returnedColumn === createdColumn) {
            "El bloque de columna debe devolver la columna declarada."
        }

        return createdColumn
    }

    /**
     * Declara la unica columna permitida por este builder.
     */
    override fun addColumn(name: String, type: String): ColumnDefinition {
        require(column == null) {
            "Solo puedes declarar una columna por llamada a addColumn."
        }

        val columnName = SqlIdentifier.requireValid(name, "Nombre de columna")

        return ColumnDefinition(columnName, type).also { createdColumn ->
            column = createdColumn
        }
    }
}

package kernel.database.schema

import kernel.database.support.SqlIdentifier

/**
 * Builder usado por `createTable` para declarar la estructura de una tabla.
 */
class TableBlueprint internal constructor(tableName: String) : SchemaColumnBlueprint() {
    internal val name: String = SqlIdentifier.requireQualified(tableName, "Nombre de tabla")

    private val columns = mutableListOf<ColumnDefinition>()
    private val constraints = mutableListOf<TableConstraintDefinition>()
    private var primaryKey: PrimaryKeyDefinition? = null

    private val portableDsl = PortableTableDefinitionDsl(
        tableName = name,
        addConstraint = constraints::add,
        setPrimaryKey = ::setPrimaryKeyDefinition
    )

    private val postgresDsl = PostgresTableDefinitionDsl(
        addConstraint = constraints::add
    )

    fun primaryKey(vararg columns: String) {
        portableDsl.primaryKey(*columns)
    }

    fun unique(vararg columns: String, name: String? = null) {
        portableDsl.unique(*columns, name = name)
    }

    fun check(name: String, expression: String) {
        portableDsl.check(name, expression)
    }

    fun foreign(vararg columns: String, name: String? = null): ForeignKeyDefinition {
        return portableDsl.foreign(*columns, name = name)
    }

    fun exclude(
        name: String,
        using: String,
        vararg elements: String,
        where: String? = null
    ) {
        postgresDsl.exclude(name, using, elements, where)
    }

    internal fun build(): TableDefinition {
        require(columns.isNotEmpty()) {
            "La tabla '$name' debe contener al menos una columna."
        }

        val columnNames = columns.mapTo(linkedSetOf()) { column -> column.name }
        val fluentPrimaryColumns = columns
            .filter { column -> column.isPrimaryKey }
            .map { column -> column.name }
        val resolvedPrimaryKey = when {
            primaryKey != null && fluentPrimaryColumns.isNotEmpty() -> {
                val explicitColumns = primaryKey?.columns.orEmpty()
                PrimaryKeyDefinition((explicitColumns + fluentPrimaryColumns).distinct())
            }
            primaryKey != null -> primaryKey
            fluentPrimaryColumns.isNotEmpty() -> PrimaryKeyDefinition(fluentPrimaryColumns.distinct())
            else -> null
        }
        val missingPrimaryColumns = resolvedPrimaryKey
            ?.columns
            ?.filterNot { column -> column in columnNames }
            .orEmpty()

        require(missingPrimaryColumns.isEmpty()) {
            "La llave primaria referencia columnas inexistentes: ${missingPrimaryColumns.joinToString(", ")}."
        }

        return TableDefinition(
            name = name,
            columns = columns.toList(),
            primaryKey = resolvedPrimaryKey,
            constraints = constraints.toList()
        )
    }

    override fun addColumn(name: String, type: String): ColumnDefinition {
        val columnName = SqlIdentifier.requireValid(name, "Nombre de columna")

        require(columns.none { column -> column.name == columnName }) {
            "La columna '$columnName' ya existe en la tabla '$name'."
        }

        return ColumnDefinition(columnName, type).also { column ->
            columns += column
        }
    }

    private fun setPrimaryKeyDefinition(definition: PrimaryKeyDefinition) {
        primaryKey = definition
    }
}

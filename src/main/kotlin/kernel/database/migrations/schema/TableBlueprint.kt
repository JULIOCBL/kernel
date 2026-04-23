package kernel.database.migrations.schema

import kernel.database.migrations.support.SqlIdentifier

/**
 * Builder usado por `createTable` para declarar columnas PostgreSQL.
 */
class TableBlueprint internal constructor(tableName: String) : PostgreSqlColumnBlueprint() {
    internal val name: String = SqlIdentifier.requireQualified(tableName, "Nombre de tabla")

    private val columns = mutableListOf<ColumnDefinition>()
    private val constraints = mutableListOf<TableConstraintDefinition>()
    private var primaryKey: PrimaryKeyDefinition? = null

    /**
     * Define una llave primaria compuesta o simple a nivel de tabla.
     */
    fun primaryKey(vararg columns: String) {
        require(columns.isNotEmpty()) {
            "La llave primaria debe contener al menos una columna."
        }

        val normalizedColumns = columns.map { column ->
            SqlIdentifier.requireValid(column, "Columna de llave primaria")
        }

        require(normalizedColumns.distinct().size == normalizedColumns.size) {
            "La llave primaria no puede repetir columnas."
        }

        primaryKey = PrimaryKeyDefinition(normalizedColumns)
    }

    /**
     * Agrega una constraint `UNIQUE` a nivel de tabla.
     */
    fun unique(vararg columns: String, name: String? = null) {
        val normalizedColumns = columnNames(columns.toList())

        constraints += UniqueConstraintDefinition(
            name = name?.let { value -> SqlIdentifier.requireValid(value, "Nombre de constraint") }
                ?: defaultName(normalizedColumns, "unique"),
            columns = normalizedColumns
        )
    }

    /**
     * Agrega una constraint `CHECK` a nivel de tabla.
     */
    fun check(name: String, expression: String) {
        constraints += CheckConstraintDefinition(
            name = SqlIdentifier.requireValid(name, "Nombre de constraint"),
            expression = sqlFragment(expression, "Expresion CHECK")
        )
    }

    /**
     * Agrega una foreign key a nivel de tabla y devuelve su builder fluido.
     */
    fun foreign(vararg columns: String, name: String? = null): ForeignKeyDefinition {
        val normalizedColumns = columnNames(columns.toList())
        val foreignKey = ForeignKeyDefinition(
            name = name?.let { value -> SqlIdentifier.requireValid(value, "Nombre de foreign key") }
                ?: defaultName(normalizedColumns, "foreign"),
            columns = normalizedColumns
        )

        constraints += foreignKey

        return foreignKey
    }

    /**
     * Agrega una constraint PostgreSQL `EXCLUDE`.
     */
    fun exclude(
        name: String,
        using: String,
        vararg elements: String,
        where: String? = null
    ) {
        require(elements.isNotEmpty()) {
            "Debes indicar al menos un elemento para EXCLUDE."
        }

        constraints += ExcludeConstraintDefinition(
            name = SqlIdentifier.requireValid(name, "Nombre de constraint"),
            using = SqlIdentifier.requireValid(using, "Metodo de indice"),
            elements = elements.map { element -> sqlFragment(element, "Elemento EXCLUDE") },
            where = where?.let { expression -> sqlFragment(expression, "Expresion WHERE") }
        )
    }

    /**
     * Valida y construye la definicion inmutable de la tabla.
     */
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

    /**
     * Agrega una columna a la tabla evitando nombres duplicados.
     */
    override fun addColumn(name: String, type: String): ColumnDefinition {
        val columnName = SqlIdentifier.requireValid(name, "Nombre de columna")

        require(columns.none { column -> column.name == columnName }) {
            "La columna '$columnName' ya existe en la tabla '$name'."
        }

        return ColumnDefinition(columnName, type).also { column ->
            columns += column
        }
    }

    /**
     * Valida una lista no vacia de columnas sin duplicados.
     */
    private fun columnNames(columns: List<String>): List<String> {
        require(columns.isNotEmpty()) {
            "Debes indicar al menos una columna."
        }

        val normalizedColumns = columns.map { column ->
            SqlIdentifier.requireValid(column, "Nombre de columna")
        }

        require(normalizedColumns.distinct().size == normalizedColumns.size) {
            "No puedes repetir columnas."
        }

        return normalizedColumns
    }

    /**
     * Genera nombres convencionales para constraints cuando no se especifican.
     */
    private fun defaultName(columns: List<String>, suffix: String): String {
        return "${name.substringAfterLast('.')}_${columns.joinToString("_")}_$suffix"
    }

    /**
     * Normaliza expresiones SQL libres usadas en constraints.
     */
    private fun sqlFragment(value: String, label: String): String {
        val fragment = value.trim()

        require(fragment.isNotEmpty()) {
            "$label no puede estar vacio."
        }

        return fragment
    }
}

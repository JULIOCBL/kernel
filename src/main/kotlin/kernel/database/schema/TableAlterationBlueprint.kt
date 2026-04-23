package kernel.database.migrations.schema

import kernel.database.migrations.collector.MigrationCollector
import kernel.database.migrations.statements.AddColumnStatement
import kernel.database.migrations.statements.AddTableConstraintStatement
import kernel.database.migrations.statements.AlterColumnTypeStatement
import kernel.database.migrations.statements.CreateIndexStatement
import kernel.database.migrations.statements.DropColumnDefaultStatement
import kernel.database.migrations.statements.DropColumnNotNullStatement
import kernel.database.migrations.statements.DropColumnStatement
import kernel.database.migrations.statements.DropConstraintStatement
import kernel.database.migrations.statements.DropIndexStatement
import kernel.database.migrations.statements.RenameColumnStatement
import kernel.database.migrations.statements.RenameConstraintStatement
import kernel.database.migrations.statements.SetColumnDefaultStatement
import kernel.database.migrations.statements.SetColumnNotNullStatement
import kernel.database.migrations.support.SqlIdentifier

/**
 * Builder estilo Laravel para modificar una tabla existente.
 */
class TableAlterationBlueprint internal constructor(
    private val table: String,
    private val collector: MigrationCollector
) : PostgreSqlColumnBlueprint() {
    /**
     * Agrega una columna a una tabla existente con `ALTER TABLE ... ADD COLUMN`.
     */
    override fun addColumn(name: String, type: String): ColumnDefinition {
        val columnName = SqlIdentifier.requireValid(name, "Nombre de columna")
        val column = ColumnDefinition(columnName, type)

        collector.add(
            AddColumnStatement(
                table = table,
                column = column,
                ifNotExists = true
            )
        )

        return column
    }

    /**
     * Elimina una o varias columnas de la tabla actual.
     */
    fun dropColumn(
        vararg columns: String,
        ifExists: Boolean = true,
        cascade: Boolean = false
    ) {
        require(columns.isNotEmpty()) {
            "Debes indicar al menos una columna."
        }

        for (column in columns) {
            collector.add(
                DropColumnStatement(
                    table = table,
                    column = columnName(column),
                    ifExists = ifExists,
                    cascade = cascade
                )
            )
        }
    }

    /**
     * Renombra una columna de la tabla actual.
     */
    fun renameColumn(from: String, to: String) {
        collector.add(
            RenameColumnStatement(
                table = table,
                from = columnName(from),
                to = columnName(to)
            )
        )
    }

    /**
     * Cambia el tipo de una columna, opcionalmente con expresion `USING`.
     */
    fun changeColumnType(
        column: String,
        type: String,
        usingExpression: String? = null
    ) {
        collector.add(
            AlterColumnTypeStatement(
                table = table,
                column = columnName(column),
                type = sqlFragment(type, "Tipo de columna"),
                usingExpression = usingExpression?.let { expression ->
                    sqlFragment(expression, "Expresion USING")
                }
            )
        )
    }

    /**
     * Define el valor default de una columna con una expresion SQL.
     */
    fun setDefault(column: String, expression: String) {
        collector.add(
            SetColumnDefaultStatement(
                table = table,
                column = columnName(column),
                expression = sqlFragment(expression, "Expresion default")
            )
        )
    }

    /**
     * Elimina el default de una columna.
     */
    fun dropDefault(column: String) {
        collector.add(
            DropColumnDefaultStatement(
                table = table,
                column = columnName(column)
            )
        )
    }

    /**
     * Marca una columna como `NOT NULL`.
     */
    fun setNotNull(column: String) {
        collector.add(
            SetColumnNotNullStatement(
                table = table,
                column = columnName(column)
            )
        )
    }

    /**
     * Permite valores null en una columna.
     */
    fun dropNotNull(column: String) {
        collector.add(
            DropColumnNotNullStatement(
                table = table,
                column = columnName(column)
            )
        )
    }

    /**
     * Crea un indice normal sobre columnas de la tabla actual.
     */
    fun index(
        vararg columns: String,
        name: String = defaultIndexName(columns.toList(), "index"),
        ifNotExists: Boolean = true,
        concurrently: Boolean = false,
        using: String? = null,
        include: List<String> = emptyList(),
        where: String? = null
    ) {
        addIndex(
            name = name,
            columns = columns.toList(),
            unique = false,
            ifNotExists = ifNotExists,
            concurrently = concurrently,
            using = using,
            include = include,
            where = where
        )
    }

    /**
     * Crea un indice unico sobre columnas de la tabla actual.
     */
    fun unique(
        vararg columns: String,
        name: String = defaultIndexName(columns.toList(), "unique"),
        ifNotExists: Boolean = true,
        concurrently: Boolean = false,
        using: String? = null,
        include: List<String> = emptyList(),
        where: String? = null
    ) {
        addIndex(
            name = name,
            columns = columns.toList(),
            unique = true,
            ifNotExists = ifNotExists,
            concurrently = concurrently,
            using = using,
            include = include,
            where = where
        )
    }

    /**
     * Crea un indice `GIN`.
     */
    fun gin(
        vararg columns: String,
        name: String = defaultIndexName(columns.toList(), "gin"),
        ifNotExists: Boolean = true,
        concurrently: Boolean = false,
        where: String? = null
    ) {
        index(
            *columns,
            name = name,
            ifNotExists = ifNotExists,
            concurrently = concurrently,
            using = "gin",
            where = where
        )
    }

    /**
     * Crea un indice `GiST`.
     */
    fun gist(
        vararg columns: String,
        name: String = defaultIndexName(columns.toList(), "gist"),
        ifNotExists: Boolean = true,
        concurrently: Boolean = false,
        where: String? = null
    ) {
        index(
            *columns,
            name = name,
            ifNotExists = ifNotExists,
            concurrently = concurrently,
            using = "gist",
            where = where
        )
    }

    /**
     * Crea un indice `BRIN`.
     */
    fun brin(
        vararg columns: String,
        name: String = defaultIndexName(columns.toList(), "brin"),
        ifNotExists: Boolean = true,
        concurrently: Boolean = false,
        where: String? = null
    ) {
        index(
            *columns,
            name = name,
            ifNotExists = ifNotExists,
            concurrently = concurrently,
            using = "brin",
            where = where
        )
    }

    /**
     * Crea un indice basado en una expresion SQL.
     */
    fun indexExpression(
        name: String,
        expression: String,
        unique: Boolean = false,
        ifNotExists: Boolean = true,
        concurrently: Boolean = false,
        using: String? = null,
        include: List<String> = emptyList(),
        where: String? = null
    ) {
        collector.add(
            CreateIndexStatement(
                name = SqlIdentifier.requireValid(name, "Nombre de indice"),
                table = table,
                columns = listOf(sqlFragment(expression, "Expresion de indice")),
                unique = unique,
                ifNotExists = ifNotExists,
                concurrently = concurrently,
                using = using?.let { method -> SqlIdentifier.requireValid(method, "Metodo de indice") },
                include = columnNamesOrEmpty(include),
                where = where?.let { expressionValue -> sqlFragment(expressionValue, "Expresion WHERE") }
            )
        )
    }

    /**
     * Elimina un indice por nombre.
     */
    fun dropIndex(
        name: String,
        ifExists: Boolean = true,
        concurrently: Boolean = false
    ) {
        collector.add(
            DropIndexStatement(
                name = SqlIdentifier.requireValid(name, "Nombre de indice"),
                ifExists = ifExists,
                concurrently = concurrently
            )
        )
    }

    /**
     * Agrega una foreign key y devuelve su builder fluido.
     */
    fun foreign(vararg columns: String, name: String? = null): ForeignKeyDefinition {
        val normalizedColumns = columnNames(columns.toList())
        val foreignKey = ForeignKeyDefinition(
            name = name?.let { value -> SqlIdentifier.requireValid(value, "Nombre de foreign key") }
                ?: defaultIndexName(normalizedColumns, "foreign"),
            columns = normalizedColumns
        )

        collector.add(AddTableConstraintStatement(table, foreignKey))

        return foreignKey
    }

    /**
     * Agrega una constraint `CHECK` a la tabla actual.
     */
    fun check(name: String, expression: String) {
        collector.add(
            AddTableConstraintStatement(
                table = table,
                constraint = CheckConstraintDefinition(
                    name = SqlIdentifier.requireValid(name, "Nombre de constraint"),
                    expression = sqlFragment(expression, "Expresion CHECK")
                )
            )
        )
    }

    /**
     * Agrega una constraint `UNIQUE` a nivel de tabla.
     */
    fun uniqueConstraint(vararg columns: String, name: String? = null) {
        val normalizedColumns = columnNames(columns.toList())

        collector.add(
            AddTableConstraintStatement(
                table = table,
                constraint = UniqueConstraintDefinition(
                    name = name?.let { value -> SqlIdentifier.requireValid(value, "Nombre de constraint") }
                        ?: defaultIndexName(normalizedColumns, "unique"),
                    columns = normalizedColumns
                )
            )
        )
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

        collector.add(
            AddTableConstraintStatement(
                table = table,
                constraint = ExcludeConstraintDefinition(
                    name = SqlIdentifier.requireValid(name, "Nombre de constraint"),
                    using = SqlIdentifier.requireValid(using, "Metodo de indice"),
                    elements = elements.map { element -> sqlFragment(element, "Elemento EXCLUDE") },
                    where = where?.let { expression -> sqlFragment(expression, "Expresion WHERE") }
                )
            )
        )
    }

    /**
     * Elimina una constraint de la tabla actual.
     */
    fun dropConstraint(
        name: String,
        ifExists: Boolean = true,
        cascade: Boolean = false
    ) {
        collector.add(
            DropConstraintStatement(
                table = table,
                name = SqlIdentifier.requireValid(name, "Nombre de constraint"),
                ifExists = ifExists,
                cascade = cascade
            )
        )
    }

    /**
     * Renombra una constraint de la tabla actual.
     */
    fun renameConstraint(from: String, to: String) {
        collector.add(
            RenameConstraintStatement(
                table = table,
                from = SqlIdentifier.requireValid(from, "Nombre de constraint"),
                to = SqlIdentifier.requireValid(to, "Nombre de constraint")
            )
        )
    }

    /**
     * Construye y registra una sentencia `CREATE INDEX` reutilizada por helpers.
     */
    private fun addIndex(
        name: String,
        columns: List<String>,
        unique: Boolean,
        ifNotExists: Boolean,
        concurrently: Boolean,
        using: String?,
        include: List<String>,
        where: String?
    ) {
        collector.add(
            CreateIndexStatement(
                name = SqlIdentifier.requireValid(name, "Nombre de indice"),
                table = table,
                columns = columnNames(columns),
                unique = unique,
                ifNotExists = ifNotExists,
                concurrently = concurrently,
                using = using?.let { method -> SqlIdentifier.requireValid(method, "Metodo de indice") },
                include = columnNamesOrEmpty(include),
                where = where?.let { expression -> sqlFragment(expression, "Expresion WHERE") }
            )
        )
    }

    /**
     * Genera un nombre convencional para indices y constraints.
     */
    private fun defaultIndexName(columns: List<String>, suffix: String): String {
        return "${table.substringAfterLast('.')}_${columnNames(columns).joinToString("_")}_$suffix"
    }

    /**
     * Valida un nombre simple de columna.
     */
    private fun columnName(name: String): String {
        return SqlIdentifier.requireValid(name, "Nombre de columna")
    }

    /**
     * Valida una lista no vacia de columnas sin duplicados.
     */
    private fun columnNames(columns: List<String>): List<String> {
        require(columns.isNotEmpty()) {
            "Debes indicar al menos una columna."
        }

        val normalizedColumns = columns.map { column -> columnName(column) }

        require(normalizedColumns.distinct().size == normalizedColumns.size) {
            "No puedes repetir columnas."
        }

        return normalizedColumns
    }

    /**
     * Valida columnas solo cuando la lista opcional no esta vacia.
     */
    private fun columnNamesOrEmpty(columns: List<String>): List<String> {
        return if (columns.isEmpty()) {
            emptyList()
        } else {
            columnNames(columns)
        }
    }

    /**
     * Normaliza fragmentos SQL libres usados en indices y constraints.
     */
    private fun sqlFragment(value: String, label: String): String {
        val fragment = value.trim()

        require(fragment.isNotEmpty()) {
            "$label no puede estar vacio."
        }

        return fragment
    }
}

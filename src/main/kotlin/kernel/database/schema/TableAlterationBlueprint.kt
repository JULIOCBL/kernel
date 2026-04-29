package kernel.database.schema

import kernel.database.collector.MigrationCollector

/**
 * Builder estilo Laravel para modificar una tabla existente.
 */
class TableAlterationBlueprint internal constructor(
    private val table: String,
    private val collector: MigrationCollector
) : SchemaColumnBlueprint() {
    private val portableDsl = PortableTableAlterationDsl(table, collector)
    private val postgresDsl = PostgresTableAlterationDsl(table, collector)

    override fun addColumn(name: String, type: String): ColumnDefinition {
        return portableDsl.addColumn(name, type)
    }

    fun dropColumn(
        vararg columns: String,
        ifExists: Boolean = true,
        cascade: Boolean = false
    ) {
        portableDsl.dropColumn(columns, ifExists, cascade)
    }

    fun renameColumn(from: String, to: String) {
        portableDsl.renameColumn(from, to)
    }

    fun changeColumnType(
        column: String,
        type: String,
        usingExpression: String? = null
    ) {
        portableDsl.changeColumnType(column, type, usingExpression)
    }

    fun setDefault(column: String, expression: String) {
        portableDsl.setDefault(column, expression)
    }

    fun dropDefault(column: String) {
        portableDsl.dropDefault(column)
    }

    fun setNotNull(column: String) {
        portableDsl.setNotNull(column)
    }

    fun dropNotNull(column: String) {
        portableDsl.dropNotNull(column)
    }

    fun index(
        vararg columns: String,
        name: String = portableDsl.defaultIndexName(columns.toList(), "index"),
        ifNotExists: Boolean = true,
        concurrently: Boolean = false,
        using: String? = null,
        include: List<String> = emptyList(),
        where: String? = null
    ) {
        portableDsl.index(
            columns = columns,
            name = name,
            unique = false,
            ifNotExists = ifNotExists,
            concurrently = concurrently,
            using = using,
            include = include,
            where = where
        )
    }

    fun unique(
        vararg columns: String,
        name: String = portableDsl.defaultIndexName(columns.toList(), "unique"),
        ifNotExists: Boolean = true,
        concurrently: Boolean = false,
        using: String? = null,
        include: List<String> = emptyList(),
        where: String? = null
    ) {
        portableDsl.index(
            columns = columns,
            name = name,
            unique = true,
            ifNotExists = ifNotExists,
            concurrently = concurrently,
            using = using,
            include = include,
            where = where
        )
    }

    fun gin(
        vararg columns: String,
        name: String = portableDsl.defaultIndexName(columns.toList(), "gin"),
        ifNotExists: Boolean = true,
        concurrently: Boolean = false,
        where: String? = null
    ) {
        postgresDsl.gin(columns, name, ifNotExists, concurrently, where)
    }

    fun gist(
        vararg columns: String,
        name: String = portableDsl.defaultIndexName(columns.toList(), "gist"),
        ifNotExists: Boolean = true,
        concurrently: Boolean = false,
        where: String? = null
    ) {
        postgresDsl.gist(columns, name, ifNotExists, concurrently, where)
    }

    fun brin(
        vararg columns: String,
        name: String = portableDsl.defaultIndexName(columns.toList(), "brin"),
        ifNotExists: Boolean = true,
        concurrently: Boolean = false,
        where: String? = null
    ) {
        postgresDsl.brin(columns, name, ifNotExists, concurrently, where)
    }

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
        postgresDsl.indexExpression(
            name = name,
            expression = expression,
            unique = unique,
            ifNotExists = ifNotExists,
            concurrently = concurrently,
            using = using,
            include = include,
            where = where
        )
    }

    fun dropIndex(
        name: String,
        ifExists: Boolean = true,
        concurrently: Boolean = false
    ) {
        portableDsl.dropIndex(name, ifExists, concurrently)
    }

    fun foreign(vararg columns: String, name: String? = null): ForeignKeyDefinition {
        return portableDsl.foreign(*columns, name = name)
    }

    fun check(name: String, expression: String) {
        portableDsl.check(name, expression)
    }

    fun uniqueConstraint(vararg columns: String, name: String? = null) {
        portableDsl.uniqueConstraint(*columns, name = name)
    }

    fun exclude(
        name: String,
        using: String,
        vararg elements: String,
        where: String? = null
    ) {
        postgresDsl.exclude(name, using, elements, where)
    }

    fun dropConstraint(
        name: String,
        ifExists: Boolean = true,
        cascade: Boolean = false
    ) {
        portableDsl.dropConstraint(name, ifExists, cascade)
    }

    fun renameConstraint(from: String, to: String) {
        portableDsl.renameConstraint(from, to)
    }
}

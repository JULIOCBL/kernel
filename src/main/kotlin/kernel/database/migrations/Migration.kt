package kernel.database.migrations

import kernel.database.collector.MigrationCollector
import kernel.database.schema.ColumnBlueprint
import kernel.database.schema.ColumnDefinition
import kernel.database.schema.TableAlterationBlueprint
import kernel.database.schema.TableBlueprint

/**
 * Clase base para definir migraciones mediante un DSL pequeno.
 *
 * La fachada publica sigue viviendo aqui, pero la implementacion real ahora se
 * divide en soportes portables y soportes orientados a PostgreSQL para que la
 * arquitectura sea mas legible y extensible.
 */
abstract class Migration {
    /**
     * Nombre opcional de la conexion que esta migracion desea usar.
     *
     * Si no se define, el migrator podra usar la conexion pedida por comando o
     * la conexion default de la app.
     */
    open val connectionName: String? = null

    /**
     * Indica si la migracion debe ejecutarse dentro de una transaccion cuando
     * el driver soporte transacciones de schema.
     */
    open val withinTransaction: Boolean = true

    private var collector: MigrationCollector? = null

    private val portableDsl by lazy {
        PortableMigrationDsl(::requireActiveCollector)
    }

    private val postgresDsl by lazy {
        PostgresMigrationDsl(::requireActiveCollector)
    }

    /**
     * Convierte las operaciones de `up` a sentencias SQL.
     */
    fun upSql(): List<String> = collect { up() }

    /**
     * Convierte las operaciones de `down` a sentencias SQL.
     */
    fun downSql(): List<String> = collect { down() }

    /**
     * Define las operaciones que aplican la migracion.
     */
    abstract fun up()

    /**
     * Define las operaciones que revierten la migracion.
     */
    abstract fun down()

    protected fun create(
        name: String,
        ifNotExists: Boolean = true,
        definition: TableBlueprint.() -> Unit
    ) = createTable(name, ifNotExists, definition)

    protected fun createTable(
        name: String,
        ifNotExists: Boolean = true,
        definition: TableBlueprint.() -> Unit
    ) = portableDsl.createTable(name, ifNotExists, definition)

    protected fun table(name: String, definition: TableAlterationBlueprint.() -> Unit) {
        portableDsl.table(name, definition)
    }

    protected fun drop(name: String) {
        dropTable(name, ifExists = false)
    }

    protected fun dropIfExists(name: String) {
        dropTable(name, ifExists = true)
    }

    protected fun dropTable(name: String, ifExists: Boolean = true) {
        portableDsl.dropTable(name, ifExists)
    }

    protected fun createSchema(name: String, ifNotExists: Boolean = true) {
        postgresDsl.createSchema(name, ifNotExists)
    }

    protected fun dropSchema(
        name: String,
        ifExists: Boolean = true,
        cascade: Boolean = false
    ) {
        postgresDsl.dropSchema(name, ifExists, cascade)
    }

    protected fun renameSchema(from: String, to: String) {
        postgresDsl.renameSchema(from, to)
    }

    protected fun createExtension(
        name: String,
        ifNotExists: Boolean = true,
        schema: String? = null,
        version: String? = null
    ) {
        postgresDsl.createExtension(name, ifNotExists, schema, version)
    }

    protected fun dropExtension(
        name: String,
        ifExists: Boolean = true,
        cascade: Boolean = false
    ) {
        postgresDsl.dropExtension(name, ifExists, cascade)
    }

    protected fun createEnum(name: String, vararg values: String) {
        postgresDsl.createEnum(name, values.toList())
    }

    protected fun dropEnum(name: String, ifExists: Boolean = true) {
        postgresDsl.dropEnum(name, ifExists)
    }

    protected fun addEnumValue(
        name: String,
        value: String,
        ifNotExists: Boolean = true,
        before: String? = null,
        after: String? = null
    ) {
        postgresDsl.addEnumValue(name, value, ifNotExists, before, after)
    }

    protected fun renameEnumValue(name: String, from: String, to: String) {
        postgresDsl.renameEnumValue(name, from, to)
    }

    protected fun renameEnum(from: String, to: String) {
        postgresDsl.renameEnum(from, to)
    }

    protected fun createDomain(
        name: String,
        type: String,
        notNull: Boolean = false,
        defaultExpression: String? = null,
        checkExpression: String? = null
    ) {
        postgresDsl.createDomain(name, type, notNull, defaultExpression, checkExpression)
    }

    protected fun dropDomain(
        name: String,
        ifExists: Boolean = true,
        cascade: Boolean = false
    ) {
        postgresDsl.dropDomain(name, ifExists, cascade)
    }

    protected fun addColumn(
        table: String,
        ifNotExists: Boolean = true,
        definition: ColumnBlueprint.() -> ColumnDefinition
    ) {
        portableDsl.addColumn(table, ifNotExists, definition)
    }

    protected fun dropColumn(
        table: String,
        column: String,
        ifExists: Boolean = true,
        cascade: Boolean = false
    ) {
        portableDsl.dropColumn(table, column, ifExists, cascade)
    }

    protected fun renameColumn(table: String, from: String, to: String) {
        portableDsl.renameColumn(table, from, to)
    }

    protected fun rename(from: String, to: String) {
        renameTable(from, to)
    }

    protected fun renameTable(from: String, to: String) {
        portableDsl.renameTable(from, to)
    }

    protected fun alterColumnType(
        table: String,
        column: String,
        type: String,
        usingExpression: String? = null
    ) {
        portableDsl.alterColumnType(table, column, type, usingExpression)
    }

    protected fun setColumnDefault(table: String, column: String, expression: String) {
        portableDsl.setColumnDefault(table, column, expression)
    }

    protected fun dropColumnDefault(table: String, column: String) {
        portableDsl.dropColumnDefault(table, column)
    }

    protected fun setColumnNotNull(table: String, column: String) {
        portableDsl.setColumnNotNull(table, column)
    }

    protected fun dropColumnNotNull(table: String, column: String) {
        portableDsl.dropColumnNotNull(table, column)
    }

    protected fun dropConstraint(
        table: String,
        name: String,
        ifExists: Boolean = true,
        cascade: Boolean = false
    ) {
        portableDsl.dropConstraint(table, name, ifExists, cascade)
    }

    protected fun renameConstraint(table: String, from: String, to: String) {
        portableDsl.renameConstraint(table, from, to)
    }

    protected fun createIndex(
        name: String,
        table: String,
        vararg columns: String,
        unique: Boolean = false,
        ifNotExists: Boolean = true,
        concurrently: Boolean = false,
        using: String? = null,
        include: List<String> = emptyList(),
        where: String? = null
    ) {
        portableDsl.createIndex(
            name = name,
            table = table,
            columns = columns.toList(),
            unique = unique,
            ifNotExists = ifNotExists,
            concurrently = concurrently,
            using = using,
            include = include,
            where = where
        )
    }

    protected fun dropIndex(
        name: String,
        ifExists: Boolean = true,
        concurrently: Boolean = false
    ) {
        portableDsl.dropIndex(name, ifExists, concurrently)
    }

    protected fun createView(
        name: String,
        query: String,
        orReplace: Boolean = true
    ) {
        portableDsl.createView(name, query, orReplace)
    }

    protected fun dropView(
        name: String,
        ifExists: Boolean = true,
        cascade: Boolean = false
    ) {
        portableDsl.dropView(name, ifExists, cascade)
    }

    protected fun createMaterializedView(
        name: String,
        query: String,
        ifNotExists: Boolean = true,
        withData: Boolean = true
    ) {
        postgresDsl.createMaterializedView(name, query, ifNotExists, withData)
    }

    protected fun dropMaterializedView(
        name: String,
        ifExists: Boolean = true,
        cascade: Boolean = false
    ) {
        postgresDsl.dropMaterializedView(name, ifExists, cascade)
    }

    protected fun refreshMaterializedView(
        name: String,
        concurrently: Boolean = false,
        withData: Boolean = true
    ) {
        postgresDsl.refreshMaterializedView(name, concurrently, withData)
    }

    protected fun createSequence(
        name: String,
        ifNotExists: Boolean = true,
        incrementBy: Long? = null,
        minValue: Long? = null,
        maxValue: Long? = null,
        startWith: Long? = null,
        cache: Long? = null,
        cycle: Boolean = false,
        ownedBy: String? = null
    ) {
        postgresDsl.createSequence(
            name = name,
            ifNotExists = ifNotExists,
            incrementBy = incrementBy,
            minValue = minValue,
            maxValue = maxValue,
            startWith = startWith,
            cache = cache,
            cycle = cycle,
            ownedBy = ownedBy
        )
    }

    protected fun dropSequence(
        name: String,
        ifExists: Boolean = true,
        cascade: Boolean = false
    ) {
        postgresDsl.dropSequence(name, ifExists, cascade)
    }

    protected fun renameSequence(from: String, to: String) {
        postgresDsl.renameSequence(from, to)
    }

    protected fun commentOnTable(table: String, comment: String?) {
        postgresDsl.commentOnTable(table, comment)
    }

    protected fun commentOnColumn(table: String, column: String, comment: String?) {
        postgresDsl.commentOnColumn(table, column, comment)
    }

    protected fun createFunction(
        name: String,
        returns: String,
        language: String,
        body: String,
        arguments: String = "",
        orReplace: Boolean = true
    ) {
        postgresDsl.createFunction(name, returns, language, body, arguments, orReplace)
    }

    protected fun dropFunction(
        signature: String,
        ifExists: Boolean = true,
        cascade: Boolean = false
    ) {
        postgresDsl.dropFunction(signature, ifExists, cascade)
    }

    protected fun createTrigger(
        name: String,
        table: String,
        timing: String,
        vararg events: String,
        function: String,
        forEach: String = "ROW",
        whenExpression: String? = null
    ) {
        postgresDsl.createTrigger(
            name = name,
            table = table,
            timing = timing,
            events = events.toList(),
            function = function,
            forEach = forEach,
            whenExpression = whenExpression
        )
    }

    protected fun dropTrigger(
        name: String,
        table: String,
        ifExists: Boolean = true,
        cascade: Boolean = false
    ) {
        postgresDsl.dropTrigger(name, table, ifExists, cascade)
    }

    protected fun statement(sql: String) {
        portableDsl.statement(sql)
    }

    private fun collect(block: () -> Unit): List<String> {
        val previousCollector = collector
        val currentCollector = MigrationCollector()

        collector = currentCollector

        try {
            block()
            return currentCollector.toSql()
        } finally {
            collector = previousCollector
        }
    }

    internal fun requireActiveCollector(): MigrationCollector {
        return collector
            ?: error("Las operaciones de migracion solo se pueden usar al generar SQL.")
    }
}

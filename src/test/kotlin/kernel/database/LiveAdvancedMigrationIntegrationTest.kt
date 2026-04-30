package kernel.database

import kernel.database.migrations.JdbcMigrationRepository
import kernel.database.migrations.Migration
import kernel.database.migrations.MigrationDefinition
import kernel.database.migrations.MigrationRegistry
import kernel.database.migrations.MigrationRollbackOptions
import kernel.database.migrations.MigrationRunOptions
import kernel.database.migrations.MigrationState
import kernel.database.migrations.MigrationStatusOptions
import kernel.database.migrations.Migrator
import kernel.database.pdo.drivers.DatabaseDriver
import kernel.database.pdo.drivers.MariaDbDriver
import kernel.database.pdo.drivers.PostgreSqlDriver
import kernel.foundation.Application
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Files
import java.sql.Connection
import java.util.Comparator
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LiveAdvancedMigrationIntegrationTest {
    @Test
    fun `runs advanced migration lifecycle against live postgresql`() {
        val target = postgresTarget()
        assumeDatabaseAvailable(target)

        val application = buildApplication()
        val (namespace, values) = databaseConfig()
        application.loadConfig(namespace, values)
        val manager = DatabaseManager.from(application)
        val suffix = System.currentTimeMillis().toString()
        val names = PostgresFeatureNames(suffix)
        val createName = "live_postgres_feature_create_$suffix"
        val updateName = "live_postgres_feature_update_$suffix"
        val migrator = Migrator(
            repository = JdbcMigrationRepository(manager, "kernel_live_pg_feature_${suffix}_migrations"),
            resolver = manager,
            registry = MigrationRegistry(
                listOf(
                    MigrationDefinition(
                        name = createName,
                        type = CreatePostgresFeatureMigration::class,
                        creator = { CreatePostgresFeatureMigration(names, target.connectionName) }
                    ),
                    MigrationDefinition(
                        name = updateName,
                        type = UpdatePostgresFeatureMigration::class,
                        creator = { UpdatePostgresFeatureMigration(names, target.connectionName) }
                    )
                )
            )
        )

        try {
            cleanupPostgresArtifacts(manager, target, names, suffix)

            val createExecuted = migrator.run(
                MigrationRunOptions(
                    database = mariaDbTarget().connectionName,
                    only = setOf(createName)
                )
            )
            assertEquals(listOf(createName), createExecuted)
            assertTrue(schemaExists(manager, target, names.schema))
            assertTrue(tableExists(manager, target, names.schema, names.customersTable))
            assertTrue(tableExists(manager, target, names.schema, names.ordersTable))
            assertTrue(tableExists(manager, target, "public", names.logTable))
            assertTrue(enumExists(manager, target, names.enumName))
            assertTrue(domainExists(manager, target, names.domainName))
            assertTrue(sequenceExists(manager, target, names.sequenceName))
            assertTrue(columnExists(manager, target, names.schema, names.ordersTable, "metadata"))
            assertFalse(columnExists(manager, target, names.schema, names.ordersTable, "reference_code"))

            val updateExecuted = migrator.run(MigrationRunOptions(only = setOf(updateName)))
            assertEquals(listOf(updateName), updateExecuted)

            val statuses = migrator.status(MigrationStatusOptions()).associateBy { it.migration }
            assertEquals(MigrationState.RAN, statuses.getValue(createName).status)
            assertEquals(MigrationState.RAN, statuses.getValue(updateName).status)
            assertEquals(target.connectionName, statuses.getValue(createName).connection)
            assertEquals(1, statuses.getValue(createName).batch)
            assertEquals(2, statuses.getValue(updateName).batch)

            assertTrue(viewExists(manager, target, names.schema, names.viewName))
            assertTrue(materializedViewExists(manager, target, names.schema, names.materializedViewName))
            assertTrue(functionExists(manager, target, names.schema, names.functionName))
            assertTrue(triggerExists(manager, target, names.schema, names.ordersTable, names.triggerName))
            assertTrue(columnExists(manager, target, names.schema, names.ordersTable, "payload"))
            assertTrue(columnExists(manager, target, names.schema, names.ordersTable, "reference_code"))

            val updateRollback = migrator.rollback(MigrationRollbackOptions(steps = 1))
            assertEquals(listOf(updateName), updateRollback)
            assertFalse(viewExists(manager, target, names.schema, names.viewName))
            assertFalse(materializedViewExists(manager, target, names.schema, names.materializedViewName))
            assertFalse(functionExists(manager, target, names.schema, names.functionName))
            assertFalse(triggerExists(manager, target, names.schema, names.ordersTable, names.triggerName))
            assertTrue(columnExists(manager, target, names.schema, names.ordersTable, "metadata"))
            assertFalse(columnExists(manager, target, names.schema, names.ordersTable, "reference_code"))

            val createRollback = migrator.rollback(MigrationRollbackOptions(steps = 1))
            assertEquals(listOf(createName), createRollback)
            assertFalse(schemaExists(manager, target, names.schema))
            assertFalse(tableExists(manager, target, "public", names.logTable))
            assertFalse(enumExists(manager, target, names.enumName))
            assertFalse(domainExists(manager, target, names.domainName))
            assertFalse(sequenceExists(manager, target, names.sequenceName))
        } finally {
            cleanupPostgresArtifacts(manager, target, names, suffix)
            cleanupApplicationBasePath(application)
        }
    }

    @Test
    fun `runs advanced migration lifecycle against live mariadb`() {
        val target = mariaDbTarget()
        assumeDatabaseAvailable(target)

        val application = buildApplication()
        val (namespace, values) = databaseConfig()
        application.loadConfig(namespace, values)
        val manager = DatabaseManager.from(application)
        val suffix = System.currentTimeMillis().toString()
        val names = MariaDbFeatureNames(suffix)
        val createName = "live_mariadb_feature_create_$suffix"
        val updateName = "live_mariadb_feature_update_$suffix"
        val migrator = Migrator(
            repository = JdbcMigrationRepository(manager, "kernel_live_mariadb_feature_${suffix}_migrations"),
            resolver = manager,
            registry = MigrationRegistry(
                listOf(
                    MigrationDefinition(
                        name = createName,
                        type = CreateMariaDbFeatureMigration::class,
                        creator = { CreateMariaDbFeatureMigration(names, target.connectionName) }
                    ),
                    MigrationDefinition(
                        name = updateName,
                        type = UpdateMariaDbFeatureMigration::class,
                        creator = { UpdateMariaDbFeatureMigration(names, target.connectionName) }
                    )
                )
            )
        )

        try {
            cleanupMariaDbArtifacts(manager, target, names, suffix)

            val createExecuted = migrator.run(
                MigrationRunOptions(
                    database = postgresTarget().connectionName,
                    only = setOf(createName)
                )
            )
            assertEquals(listOf(createName), createExecuted)
            assertTrue(tableExists(manager, target, null, names.categoriesTable))
            assertTrue(tableExists(manager, target, null, names.itemsTable))
            assertTrue(tableExists(manager, target, null, names.logTable))
            assertTrue(viewExists(manager, target, target.database, names.viewName))
            assertTrue(columnExists(manager, target, target.database, names.itemsTable, "notes"))
            assertFalse(columnExists(manager, target, target.database, names.itemsTable, "reference_code"))

            val updateExecuted = migrator.run(MigrationRunOptions(only = setOf(updateName)))
            assertEquals(listOf(updateName), updateExecuted)

            val statuses = migrator.status(MigrationStatusOptions()).associateBy { it.migration }
            assertEquals(MigrationState.RAN, statuses.getValue(createName).status)
            assertEquals(MigrationState.RAN, statuses.getValue(updateName).status)
            assertEquals(target.connectionName, statuses.getValue(createName).connection)
            assertEquals(1, statuses.getValue(createName).batch)
            assertEquals(2, statuses.getValue(updateName).batch)

            assertTrue(columnExists(manager, target, target.database, names.itemsTable, "internal_notes"))
            assertTrue(columnExists(manager, target, target.database, names.itemsTable, "reference_code"))
            assertTrue(indexExists(manager, target, names.itemsTable, names.referenceCodeUnique))
            assertTrue(indexExists(manager, target, names.itemsTable, names.statusCreatedIndex))
            assertTrue(indexExists(manager, target, names.itemsTable, names.labelCreatedIndex))

            val updateRollback = migrator.rollback(MigrationRollbackOptions(steps = 1))
            assertEquals(listOf(updateName), updateRollback)
            assertTrue(columnExists(manager, target, target.database, names.itemsTable, "notes"))
            assertFalse(columnExists(manager, target, target.database, names.itemsTable, "reference_code"))
            assertFalse(indexExists(manager, target, names.itemsTable, names.referenceCodeUnique))
            assertFalse(indexExists(manager, target, names.itemsTable, names.statusCreatedIndex))
            assertFalse(indexExists(manager, target, names.itemsTable, names.labelCreatedIndex))

            val createRollback = migrator.rollback(MigrationRollbackOptions(steps = 1))
            assertEquals(listOf(createName), createRollback)
            assertFalse(viewExists(manager, target, target.database, names.viewName))
            assertFalse(tableExists(manager, target, null, names.logTable))
            assertFalse(tableExists(manager, target, null, names.itemsTable))
            assertFalse(tableExists(manager, target, null, names.categoriesTable))
        } finally {
            cleanupMariaDbArtifacts(manager, target, names, suffix)
            cleanupApplicationBasePath(application)
        }
    }

    private fun buildApplication(): Application {
        return Application.bootstrap(
            basePath = createTempDirectory("kernel-live-advanced-db-test").toAbsolutePath(),
            systemValues = emptyMap()
        )
    }

    private fun databaseConfig(): Pair<String, Map<String, Any?>> {
        val postgres = postgresTarget()
        val mariadb = mariaDbTarget()

        return "database" to mapOf(
            "default" to postgres.connectionName,
            "connections" to mapOf(
                postgres.connectionName to mapOf(
                    "driver" to postgres.driver.id,
                    "url" to postgres.jdbcUrl,
                    "username" to postgres.username,
                    "password" to postgres.password
                ),
                mariadb.connectionName to mapOf(
                    "driver" to mariadb.driver.id,
                    "url" to mariadb.jdbcUrl,
                    "username" to mariadb.username,
                    "password" to mariadb.password
                )
            )
        )
    }

    private fun cleanupPostgresArtifacts(
        manager: DatabaseManager,
        target: AdvancedLiveDatabaseTarget,
        names: PostgresFeatureNames,
        suffix: String
    ) {
        manager.withConnection(target.connectionName) { connection ->
            connection.createStatement().use { statement ->
                statement.execute("DROP TRIGGER IF EXISTS ${names.triggerName} ON ${names.schema}.${names.ordersTable}")
                statement.execute("DROP FUNCTION IF EXISTS ${names.schema}.${names.functionName}()")
                statement.execute("DROP MATERIALIZED VIEW IF EXISTS ${names.schema}.${names.materializedViewName}")
                statement.execute("DROP VIEW IF EXISTS ${names.schema}.${names.viewName}")
                statement.execute("DROP TABLE IF EXISTS ${names.logTable}")
                statement.execute("DROP TABLE IF EXISTS ${names.schema}.${names.ordersTable}")
                statement.execute("DROP TABLE IF EXISTS ${names.schema}.${names.customersTable}")
                statement.execute("DROP SEQUENCE IF EXISTS ${names.sequenceName}")
                statement.execute("DROP SEQUENCE IF EXISTS ${names.sequenceStageName}")
                statement.execute("DROP DOMAIN IF EXISTS ${names.domainName}")
                statement.execute("DROP TYPE IF EXISTS ${names.enumName}")
                statement.execute("DROP TYPE IF EXISTS ${names.enumDraftName}")
                statement.execute("DROP SCHEMA IF EXISTS ${names.schema} CASCADE")
                statement.execute("DROP SCHEMA IF EXISTS ${names.stageSchema} CASCADE")
                statement.execute("DROP TABLE IF EXISTS kernel_live_pg_feature_${suffix}_migrations")
            }
        }
    }

    private fun cleanupMariaDbArtifacts(
        manager: DatabaseManager,
        target: AdvancedLiveDatabaseTarget,
        names: MariaDbFeatureNames,
        suffix: String
    ) {
        manager.withConnection(target.connectionName) { connection ->
            connection.createStatement().use { statement ->
                statement.execute("DROP VIEW IF EXISTS ${names.viewName}")
                statement.execute("DROP TABLE IF EXISTS ${names.logTable}")
                statement.execute("DROP TABLE IF EXISTS ${names.itemsTable}")
                statement.execute("DROP TABLE IF EXISTS ${names.categoriesTable}")
                statement.execute("DROP TABLE IF EXISTS kernel_live_mariadb_feature_${suffix}_migrations")
            }
        }
    }

    private fun cleanupApplicationBasePath(application: Application) {
        Files.walk(application.basePath).use { paths ->
            paths.sorted(Comparator.reverseOrder())
                .forEach { path -> path.toFile().delete() }
        }
    }

    private fun schemaExists(manager: DatabaseManager, target: AdvancedLiveDatabaseTarget, schema: String): Boolean {
        return manager.withConnection(target.connectionName) { connection ->
            connection.booleanQuery(
                "select exists(select 1 from information_schema.schemata where schema_name = '$schema')"
            )
        }
    }

    private fun enumExists(manager: DatabaseManager, target: AdvancedLiveDatabaseTarget, name: String): Boolean {
        return manager.withConnection(target.connectionName) { connection ->
            connection.booleanQuery(
                "select exists(select 1 from pg_type where typname = '$name' and typtype = 'e')"
            )
        }
    }

    private fun domainExists(manager: DatabaseManager, target: AdvancedLiveDatabaseTarget, name: String): Boolean {
        return manager.withConnection(target.connectionName) { connection ->
            connection.booleanQuery(
                "select exists(select 1 from pg_type where typname = '$name' and typtype = 'd')"
            )
        }
    }

    private fun sequenceExists(manager: DatabaseManager, target: AdvancedLiveDatabaseTarget, name: String): Boolean {
        return manager.withConnection(target.connectionName) { connection ->
            connection.booleanQuery(
                "select exists(select 1 from pg_class where relkind = 'S' and relname = '$name')"
            )
        }
    }

    private fun functionExists(
        manager: DatabaseManager,
        target: AdvancedLiveDatabaseTarget,
        schema: String,
        name: String
    ): Boolean {
        return manager.withConnection(target.connectionName) { connection ->
            connection.booleanQuery(
                "select exists(" +
                    "select 1 from pg_proc p " +
                    "join pg_namespace n on n.oid = p.pronamespace " +
                    "where n.nspname = '$schema' and p.proname = '$name'" +
                    ")"
            )
        }
    }

    private fun triggerExists(
        manager: DatabaseManager,
        target: AdvancedLiveDatabaseTarget,
        schema: String,
        table: String,
        trigger: String
    ): Boolean {
        return manager.withConnection(target.connectionName) { connection ->
            connection.booleanQuery(
                "select exists(" +
                    "select 1 from information_schema.triggers " +
                    "where event_object_schema = '$schema' and event_object_table = '$table' and trigger_name = '$trigger'" +
                    ")"
            )
        }
    }

    private fun materializedViewExists(
        manager: DatabaseManager,
        target: AdvancedLiveDatabaseTarget,
        schema: String,
        name: String
    ): Boolean {
        return manager.withConnection(target.connectionName) { connection ->
            connection.booleanQuery(
                "select exists(select 1 from pg_matviews where schemaname = '$schema' and matviewname = '$name')"
            )
        }
    }

    private fun viewExists(
        manager: DatabaseManager,
        target: AdvancedLiveDatabaseTarget,
        schema: String,
        name: String
    ): Boolean {
        return manager.withConnection(target.connectionName) { connection ->
            connection.metaData.getTables(target.databaseOrNullForMeta(), schema, name, arrayOf("VIEW")).use { result ->
                result.next()
            }
        }
    }

    private fun tableExists(
        manager: DatabaseManager,
        target: AdvancedLiveDatabaseTarget,
        schema: String?,
        table: String
    ): Boolean {
        return manager.withConnection(target.connectionName) { connection ->
            connection.metaData.getTables(target.databaseOrNullForMeta(), schema, table, arrayOf("TABLE")).use { result ->
                result.next()
            }
        }
    }

    private fun columnExists(
        manager: DatabaseManager,
        target: AdvancedLiveDatabaseTarget,
        schema: String?,
        table: String,
        column: String
    ): Boolean {
        return manager.withConnection(target.connectionName) { connection ->
            connection.metaData.getColumns(target.databaseOrNullForMeta(), schema, table, column).use { result ->
                result.next()
            }
        }
    }

    private fun indexExists(
        manager: DatabaseManager,
        target: AdvancedLiveDatabaseTarget,
        table: String,
        index: String
    ): Boolean {
        return manager.withConnection(target.connectionName) { connection ->
            connection.booleanQuery(
                "select exists(" +
                    "select 1 from information_schema.statistics " +
                    "where table_schema = '${target.database}' and table_name = '$table' and index_name = '$index'" +
                    ")"
            )
        }
    }

    private fun assumeDatabaseAvailable(target: AdvancedLiveDatabaseTarget) {
        assumeTrue(
            target.missingVariables.isEmpty(),
            "Faltan credenciales para `${target.connectionName}`. " +
                "Define: ${target.missingVariables.joinToString(", ")}"
        )
        assumeTrue(
            canConnect(target),
            "No se pudo abrir la base ${target.connectionName} en ${target.host}:${target.port}/${target.database}."
        )
    }

    private fun canConnect(target: AdvancedLiveDatabaseTarget): Boolean {
        return runCatching {
            Class.forName(target.driver.defaultJdbcDriverClass)
            java.sql.DriverManager.getConnection(target.jdbcUrl, target.username, target.password).use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeQuery("select 1").use { result -> result.next() }
                }
            }
        }.getOrDefault(false)
    }

    private fun postgresTarget(): AdvancedLiveDatabaseTarget {
        return requiredTarget(
            envPrefix = "KERNEL_TEST_PG",
            connectionName = "pgsql_live",
            driver = PostgreSqlDriver,
            productKeywords = setOf("postgresql")
        )
    }

    private fun mariaDbTarget(): AdvancedLiveDatabaseTarget {
        return requiredTarget(
            envPrefix = "KERNEL_TEST_MARIADB",
            connectionName = "mariadb_live",
            driver = MariaDbDriver,
            productKeywords = setOf("mariadb", "mysql")
        )
    }

    private fun requiredTarget(
        envPrefix: String,
        connectionName: String,
        driver: DatabaseDriver,
        productKeywords: Set<String>
    ): AdvancedLiveDatabaseTarget {
        val hostKey = "${envPrefix}_HOST"
        val portKey = "${envPrefix}_PORT"
        val databaseKey = "${envPrefix}_DATABASE"
        val usernameKey = "${envPrefix}_USERNAME"
        val passwordKey = "${envPrefix}_PASSWORD"
        val rawValues = mapOf(
            hostKey to System.getenv(hostKey),
            portKey to System.getenv(portKey),
            databaseKey to System.getenv(databaseKey),
            usernameKey to System.getenv(usernameKey),
            passwordKey to System.getenv(passwordKey)
        )
        val missingVariables = rawValues.filterValues { it.isNullOrBlank() }.keys

        return AdvancedLiveDatabaseTarget(
            connectionName = connectionName,
            driver = driver,
            host = rawValues[hostKey].orEmpty(),
            port = rawValues[portKey]?.toIntOrNull() ?: 0,
            database = rawValues[databaseKey].orEmpty(),
            username = rawValues[usernameKey].orEmpty(),
            password = rawValues[passwordKey].orEmpty(),
            productKeywords = productKeywords,
            missingVariables = missingVariables
        )
    }
}

private data class AdvancedLiveDatabaseTarget(
    val connectionName: String,
    val driver: DatabaseDriver,
    val host: String,
    val port: Int,
    val database: String,
    val username: String,
    val password: String,
    val productKeywords: Set<String>,
    val missingVariables: Set<String> = emptySet()
) {
    val jdbcUrl: String
        get() = driver.buildJdbcUrl(host, port.toString(), database)

    fun databaseOrNullForMeta(): String? {
        return if (driver.id == "mariadb") {
            database
        } else {
            null
        }
    }
}

private data class PostgresFeatureNames(
    val suffix: String,
    val stageSchema: String = "kernel_live_pg_stage_$suffix",
    val schema: String = "kernel_live_pg_$suffix",
    val enumDraftName: String = "kernel_live_pg_status_draft_$suffix",
    val enumName: String = "kernel_live_pg_status_$suffix",
    val domainName: String = "kernel_live_pg_money_$suffix",
    val sequenceStageName: String = "kernel_live_pg_ref_stage_$suffix",
    val sequenceName: String = "kernel_live_pg_ref_$suffix",
    val customersTable: String = "customers",
    val ordersTable: String = "orders",
    val logTable: String = "kernel_live_pg_log_$suffix",
    val viewName: String = "open_orders",
    val materializedViewName: String = "order_totals",
    val functionName: String = "touch_updated_at",
    val triggerName: String = "orders_touch_updated_at"
)

private data class MariaDbFeatureNames(
    val suffix: String,
    val categoriesTable: String = "kernel_live_mariadb_categories_$suffix",
    val itemsTable: String = "kernel_live_mariadb_items_$suffix",
    val logTable: String = "kernel_live_mariadb_log_$suffix",
    val viewName: String = "kernel_live_mariadb_active_items_$suffix",
    val referenceCodeUnique: String = "kernel_live_mariadb_reference_code_unique_$suffix",
    val statusCreatedIndex: String = "kernel_live_mariadb_status_created_index_$suffix",
    val labelCreatedIndex: String = "kernel_live_mariadb_label_created_index_$suffix"
)

private class CreatePostgresFeatureMigration(
    private val names: PostgresFeatureNames,
    override val connectionName: String
) : Migration() {
    override fun up() {
        createSchema(names.stageSchema)
        renameSchema(names.stageSchema, names.schema)

        createEnum(names.enumDraftName, "draft", "published", "archived")
        addEnumValue(names.enumDraftName, "review", after = "draft")
        renameEnumValue(names.enumDraftName, "archived", "retired")
        renameEnum(names.enumDraftName, names.enumName)

        createDomain(
            name = names.domainName,
            type = "NUMERIC(12, 2)",
            notNull = true,
            defaultExpression = "0",
            checkExpression = "VALUE >= 0"
        )

        createSequence(
            name = names.sequenceStageName,
            incrementBy = 1,
            startWith = 1000,
            cache = 5,
            cycle = true
        )
        renameSequence(names.sequenceStageName, names.sequenceName)

        createTable("${names.schema}.${names.customersTable}") {
            id().primaryKey()
            string("name", 120).notNull()
            string("email", 180).unique()
            timestampsTz()
        }

        create("${names.schema}.${names.ordersTable}") {
            id().primaryKey()
            foreignUuid("customer_id").notNull()
            enumColumn("status", names.enumName).notNull().default("draft")
            numeric("subtotal", 12, 2).notNull().default(0)
            numeric("tax", 12, 2).notNull().default(0)
            numeric("total", 12, 2).storedAs("subtotal + tax")
            custom("amount_due", names.domainName).notNull()
            bigInt("reference_number").notNull().defaultRaw("nextval('${names.sequenceName}')")
            jsonb("metadata").notNull().defaultRaw("'{}'::jsonb")
            timestampTz("published_at")
            timestampsTz()
            softDeletesTz()

            unique("reference_number", name = "${names.schema}_${names.ordersTable}_reference_number_unique")
            check("${names.schema}_${names.ordersTable}_amount_due_check", "amount_due >= 0")
            foreign("customer_id")
                .references("id")
                .on("${names.schema}.${names.customersTable}")
                .cascadeOnDelete()
        }

        create(names.logTable) {
            id().primaryKey()
            string("message", 255).notNull()
            timestampsTz()
        }

        commentOnTable("${names.schema}.${names.ordersTable}", "Kernel live PostgreSQL feature lab orders")
        commentOnColumn(
            "${names.schema}.${names.ordersTable}",
            "reference_number",
            "Assigned from a dedicated kernel test sequence"
        )
        statement(
            "CREATE INDEX IF NOT EXISTS ${names.schema}_${names.ordersTable}_status_statement_index " +
                "ON ${names.schema}.${names.ordersTable} (status);"
        )
    }

    override fun down() {
        dropIfExists(names.logTable)
        dropIfExists("${names.schema}.${names.ordersTable}")
        dropIfExists("${names.schema}.${names.customersTable}")
        dropSequence(names.sequenceName)
        dropDomain(names.domainName)
        dropEnum(names.enumName)
        dropSchema(names.schema, cascade = true)
    }
}

private class UpdatePostgresFeatureMigration(
    private val names: PostgresFeatureNames,
    override val connectionName: String
) : Migration() {
    override fun up() {
        table("${names.schema}.${names.ordersTable}") {
            string("reference_code", 64)
            renameColumn("metadata", "payload")
            changeColumnType("reference_code", "VARCHAR(120)")
            setDefault("reference_code", "'probe-ref'")
            dropDefault("reference_code")
            setNotNull("reference_code")
            dropNotNull("published_at")
            index(
                "customer_id",
                "created_at",
                name = "${names.schema}_${names.ordersTable}_customer_created_at_index"
            )
            gin(
                "payload",
                name = "${names.schema}_${names.ordersTable}_payload_gin",
                where = "payload IS NOT NULL"
            )
            indexExpression(
                name = "${names.schema}_${names.ordersTable}_lower_reference_code_index",
                expression = "lower(reference_code)"
            )
            uniqueConstraint(
                "reference_code",
                name = "${names.schema}_${names.ordersTable}_reference_code_unique"
            )
            check(
                "${names.schema}_${names.ordersTable}_reference_code_length_check",
                "char_length(reference_code) >= 3"
            )
            renameConstraint(
                "${names.schema}_${names.ordersTable}_reference_code_length_check",
                "${names.schema}_${names.ordersTable}_reference_code_size_check"
            )
            dropConstraint("${names.schema}_${names.ordersTable}_reference_code_size_check")
        }

        addColumn("${names.schema}.${names.ordersTable}", ifNotExists = false) {
            string("temp_note", 60)
        }
        dropColumn("${names.schema}.${names.ordersTable}", "temp_note", ifExists = false)

        createIndex(
            "${names.schema}_${names.ordersTable}_status_created_index",
            "${names.schema}.${names.ordersTable}",
            "status",
            "created_at"
        )
        dropIndex("${names.schema}_${names.ordersTable}_status_created_index")

        createView(
            "${names.schema}.${names.viewName}",
            "SELECT * FROM ${names.schema}.${names.ordersTable} WHERE deleted_at IS NULL"
        )
        createMaterializedView(
            "${names.schema}.${names.materializedViewName}",
            "SELECT current_date AS calculated_on, COALESCE(sum(amount_due), 0)::numeric AS grand_total " +
                "FROM ${names.schema}.${names.ordersTable}",
            withData = false
        )
        refreshMaterializedView("${names.schema}.${names.materializedViewName}")

        createFunction(
            name = "${names.schema}.${names.functionName}",
            returns = "trigger",
            language = "plpgsql",
            body = "BEGIN\n    NEW.updated_at = CURRENT_TIMESTAMP;\n    RETURN NEW;\nEND"
        )
        createTrigger(
            name = names.triggerName,
            table = "${names.schema}.${names.ordersTable}",
            timing = "before",
            "update",
            function = "${names.schema}.${names.functionName}()"
        )
    }

    override fun down() {
        dropTrigger(names.triggerName, "${names.schema}.${names.ordersTable}")
        dropFunction("${names.schema}.${names.functionName}()")
        dropMaterializedView("${names.schema}.${names.materializedViewName}")
        dropView("${names.schema}.${names.viewName}")

        table("${names.schema}.${names.ordersTable}") {
            dropConstraint("${names.schema}_${names.ordersTable}_reference_code_unique")
            dropIndex("${names.schema}_${names.ordersTable}_payload_gin")
            dropIndex("${names.schema}_${names.ordersTable}_customer_created_at_index")
            dropIndex("${names.schema}_${names.ordersTable}_lower_reference_code_index")
            renameColumn("payload", "metadata")
            dropColumn("reference_code", ifExists = false)
        }
    }
}

private class CreateMariaDbFeatureMigration(
    private val names: MariaDbFeatureNames,
    override val connectionName: String
) : Migration() {
    override fun up() {
        create(names.categoriesTable) {
            increments().primaryKey()
            string("name", 120).notNull()
            timestamps()
        }

        createTable(names.itemsTable) {
            increments().primaryKey()
            int("category_id").notNull()
            string("label", 120).notNull()
            decimal("price", 10, 2).notNull().default(0)
            tinyInteger("priority").notNull().default(1)
            mediumInteger("stock")
            year("release_year")
            enumValues("status", "draft", "published", "archived").notNull().default("draft")
            setValues("channels", "web", "mobile", "store")
            varBinary("sku_hash", 16)
            mediumText("notes")
            json("meta")
            timestamps()
            softDeletes()

            unique("label", name = "${names.itemsTable}_label_unique")
            check("${names.itemsTable}_price_check", "price >= 0")
            foreign("category_id")
                .references("id")
                .on(names.categoriesTable)
                .cascadeOnDelete()
        }

        create(names.logTable) {
            increments().primaryKey()
            string("message", 255).notNull()
            timestamps()
        }

        createView(
            names.viewName,
            "SELECT * FROM ${names.itemsTable} WHERE deleted_at IS NULL"
        )
    }

    override fun down() {
        dropView(names.viewName)
        dropIfExists(names.logTable)
        dropIfExists(names.itemsTable)
        dropIfExists(names.categoriesTable)
    }
}

private class UpdateMariaDbFeatureMigration(
    private val names: MariaDbFeatureNames,
    override val connectionName: String
) : Migration() {
    override fun up() {
        addColumn(names.itemsTable, ifNotExists = false) {
            string("reference_code", 64)
        }

        renameColumn(names.itemsTable, "notes", "internal_notes")

        table(names.itemsTable) {
            unique("reference_code", name = names.referenceCodeUnique)
            index("status", "created_at", name = names.statusCreatedIndex)
        }

        createIndex(
            names.labelCreatedIndex,
            names.itemsTable,
            "label",
            "created_at"
        )
    }

    override fun down() {
        dropIndex(names.labelCreatedIndex, table = names.itemsTable)

        table(names.itemsTable) {
            dropIndex(names.statusCreatedIndex)
            dropIndex(names.referenceCodeUnique)
        }

        renameColumn(names.itemsTable, "internal_notes", "notes")
        dropColumn(names.itemsTable, "reference_code", ifExists = false)
    }
}

private fun Connection.booleanQuery(sql: String): Boolean {
    createStatement().use { statement ->
        statement.executeQuery(sql).use { result ->
            return result.next() && result.getBoolean(1)
        }
    }
}

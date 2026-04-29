package kernel.database

import kernel.database.migrations.JdbcMigrationRepository
import kernel.database.migrations.Migration
import kernel.database.migrations.MigrationDefinition
import kernel.database.migrations.MigrationRegistry
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
import java.sql.DriverManager
import java.util.Comparator
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LiveDatabaseIntegrationTest {
    @Test
    fun `opens live postgresql connection from configured app`() {
        verifyLiveConnection(postgresTarget())
    }

    @Test
    fun `opens live mariadb connection from configured app`() {
        verifyLiveConnection(mariaDbTarget())
    }

    @Test
    fun `runs migration lifecycle against live postgresql`() {
        verifyMigrationLifecycle(postgresTarget())
    }

    @Test
    fun `runs migration lifecycle against live mariadb`() {
        verifyMigrationLifecycle(mariaDbTarget())
    }

    private fun verifyLiveConnection(target: LiveDatabaseTarget) {
        assumeDatabaseAvailable(target)

        val application = buildApplication()
        try {
            val (namespace, values) = databaseConfig()
            application.loadConfig(namespace, values)
            val manager = DatabaseManager.from(application)

            assertEquals(target.driver, manager.connectionConfig(target.connectionName).driver)
            assertTrue(manager.hasConnection(postgresTarget().connectionName))
            assertTrue(manager.hasConnection(mariaDbTarget().connectionName))

            manager.withConnection(target.connectionName) { connection ->
                connection.createStatement().use { statement ->
                    statement.executeQuery("select 1").use { result ->
                        assertTrue(result.next())
                        assertEquals(1, result.getInt(1))
                    }
                }

                val productName = connection.metaData.databaseProductName.lowercase()
                assertTrue(
                    target.productKeywords.any(productName::contains),
                    "Producto JDBC inesperado para ${target.connectionName}: $productName"
                )
            }
        } finally {
            cleanupApplicationBasePath(application)
        }
    }

    private fun verifyMigrationLifecycle(target: LiveDatabaseTarget) {
        assumeDatabaseAvailable(target)

        val application = buildApplication()
        val (namespace, values) = databaseConfig()
        application.loadConfig(namespace, values)
        val manager = DatabaseManager.from(application)
        val suffix = System.currentTimeMillis().toString()
        val tableName = "kernel_live_${target.connectionName}_$suffix"
        val repositoryTable = "${tableName}_migrations"
        val migrationName = "live_${target.connectionName}_$suffix"
        val migrationDefinition = MigrationDefinition(
            name = migrationName,
            type = LiveMigration::class,
            creator = { LiveMigration(tableName, target.connectionName) }
        )
        val migrator = Migrator(
            repository = JdbcMigrationRepository(manager, repositoryTable),
            resolver = manager,
            registry = MigrationRegistry(listOf(migrationDefinition))
        )

        try {
            cleanupDatabaseArtifacts(manager, target, tableName, repositoryTable)

            val executed = migrator.run(MigrationRunOptions(database = postgresTarget().connectionName))
            assertEquals(listOf(migrationName), executed)
            assertTrue(tableExists(manager, target, tableName), "La tabla de prueba no fue creada en ${target.connectionName}.")

            val ranStatus = migrator.status(
                MigrationStatusOptions(database = mariaDbTarget().connectionName)
            ).single()
            assertEquals(MigrationState.RAN, ranStatus.status)
            assertEquals(target.connectionName, ranStatus.connection)

            val pendingAfterRun = migrator.pendingMigrations()
            assertTrue(pendingAfterRun.isEmpty(), "La migracion deberia figurar como ejecutada en ${target.connectionName}.")

            val rolledBack = migrator.rollback()
            assertEquals(listOf(migrationName), rolledBack)
            assertTrue(!tableExists(manager, target, tableName), "La tabla de prueba no fue eliminada en ${target.connectionName}.")

            val pendingAfterRollback = migrator.pendingMigrations().map(MigrationDefinition::name)
            assertEquals(listOf(migrationName), pendingAfterRollback)
        } finally {
            cleanupDatabaseArtifacts(manager, target, tableName, repositoryTable)
            cleanupApplicationBasePath(application)
        }
    }

    private fun buildApplication(): Application {
        return Application.bootstrap(
            basePath = createTempDirectory("kernel-live-db-test").toAbsolutePath(),
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

    private fun cleanupDatabaseArtifacts(
        manager: DatabaseManager,
        target: LiveDatabaseTarget,
        tableName: String,
        repositoryTable: String
    ) {
        manager.withConnection(target.connectionName) { connection ->
            connection.createStatement().use { statement ->
                statement.execute(dropTableSql(target, tableName))
                statement.execute(dropTableSql(target, repositoryTable))
            }
        }
    }

    private fun cleanupApplicationBasePath(application: Application) {
        Files.walk(application.basePath).use { paths ->
            paths.sorted(Comparator.reverseOrder())
                .forEach { path -> path.toFile().delete() }
        }
    }

    private fun tableExists(
        manager: DatabaseManager,
        target: LiveDatabaseTarget,
        tableName: String
    ): Boolean {
        return manager.withConnection(target.connectionName) { connection ->
            when (target.driver.id) {
                "mariadb" -> {
                    connection.metaData.getTables(target.database, null, tableName, arrayOf("TABLE")).use { result ->
                        result.next()
                    }
                }

                else -> {
                    connection.metaData.getTables(null, "public", tableName, arrayOf("TABLE")).use { result ->
                        result.next()
                    }
                }
            }
        }
    }

    private fun dropTableSql(target: LiveDatabaseTarget, tableName: String): String {
        return when (target.driver.id) {
            "mariadb" -> "drop table if exists `${tableName}`"
            else -> "drop table if exists \"${tableName}\""
        }
    }

    private fun assumeDatabaseAvailable(target: LiveDatabaseTarget) {
        assumeTrue(
            canConnect(target),
            "No se pudo abrir la base ${target.connectionName} en ${target.host}:${target.port}/${target.database}."
        )
    }

    private fun canConnect(target: LiveDatabaseTarget): Boolean {
        return runCatching {
            Class.forName(target.driver.defaultJdbcDriverClass)
            DriverManager.getConnection(target.jdbcUrl, target.username, target.password).use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeQuery("select 1").use { result ->
                        result.next()
                    }
                }
            }
        }.getOrDefault(false)
    }

    private fun postgresTarget(): LiveDatabaseTarget {
        return LiveDatabaseTarget(
            connectionName = "pgsql_live",
            driver = PostgreSqlDriver,
            host = System.getenv("KERNEL_TEST_PG_HOST") ?: "localhost",
            port = (System.getenv("KERNEL_TEST_PG_PORT") ?: "5432").toInt(),
            database = System.getenv("KERNEL_TEST_PG_DATABASE") ?: "test",
            username = System.getenv("KERNEL_TEST_PG_USERNAME") ?: "root",
            password = System.getenv("KERNEL_TEST_PG_PASSWORD") ?: "root",
            productKeywords = setOf("postgresql")
        )
    }

    private fun mariaDbTarget(): LiveDatabaseTarget {
        return LiveDatabaseTarget(
            connectionName = "mariadb_live",
            driver = MariaDbDriver,
            host = System.getenv("KERNEL_TEST_MARIADB_HOST") ?: "localhost",
            port = (System.getenv("KERNEL_TEST_MARIADB_PORT") ?: "3307").toInt(),
            database = System.getenv("KERNEL_TEST_MARIADB_DATABASE") ?: "test",
            username = System.getenv("KERNEL_TEST_MARIADB_USERNAME") ?: "root",
            password = System.getenv("KERNEL_TEST_MARIADB_PASSWORD") ?: "root",
            productKeywords = setOf("mariadb", "mysql")
        )
    }
}

private data class LiveDatabaseTarget(
    val connectionName: String,
    val driver: DatabaseDriver,
    val host: String,
    val port: Int,
    val database: String,
    val username: String,
    val password: String,
    val productKeywords: Set<String>
) {
    val jdbcUrl: String
        get() = driver.buildJdbcUrl(host, port.toString(), database)
}

private class LiveMigration(
    private val tableName: String,
    override val connectionName: String
) : Migration() {
    override fun up() {
        create(tableName) {
            uuid("id").primaryKey()
            string("label", 120).notNull()
            binary("payload")
            dateTime("registered_at", precision = 3)
        }
    }

    override fun down() {
        dropIfExists(tableName)
    }
}

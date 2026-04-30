package kernel.database.migrations

import kernel.database.pdo.connections.ConnectionResolver
import kernel.database.pdo.connections.DatabaseConnectionConfig
import kernel.database.pdo.drivers.DatabaseDriver
import kernel.database.pdo.drivers.MariaDbDriver
import kernel.database.pdo.drivers.PostgreSqlDriver
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.Statement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MigratorTest {
    @Test
    fun `run falls back to resolver default connection when migration and command omit database`() {
        val repository = InMemoryMigrationRepository()
        val resolver = RecordingConnectionResolver(defaultConnectionName = "main")
        val registry = MigrationRegistry(
            listOf(
                migrationFactory(::DefaultConnectionMigration)
            )
        )
        val migrator = Migrator(repository, resolver, registry)

        val executed = migrator.run()

        assertEquals(listOf("DefaultConnectionMigration"), executed)
        assertEquals(listOf("DefaultConnectionMigration"), repository.getRan("main"))
        assertTrue(repository.getRan("logs").isEmpty())
        assertTrue(
            resolver.connectionHandle("main").executedStatements.any { statement ->
                statement.contains("default_connection_entries")
            }
        )
    }

    @Test
    fun `run uses command database when migration does not define an explicit connection`() {
        val repository = InMemoryMigrationRepository()
        val resolver = RecordingConnectionResolver(defaultConnectionName = "main")
        val registry = MigrationRegistry(
            listOf(
                migrationFactory(::DefaultConnectionMigration)
            )
        )
        val migrator = Migrator(repository, resolver, registry)

        val executed = migrator.run(MigrationRunOptions(database = "analytics"))

        assertEquals(listOf("DefaultConnectionMigration"), executed)
        assertEquals(listOf("DefaultConnectionMigration"), repository.getRan("analytics"))
        assertTrue(repository.getRan("main").isEmpty())
        assertTrue(
            resolver.connectionHandle("analytics").executedStatements.any { statement ->
                statement.contains("default_connection_entries")
            }
        )
    }

    @Test
    fun `run ignores blank migration connection names and falls back to command database`() {
        val repository = InMemoryMigrationRepository()
        val resolver = RecordingConnectionResolver(defaultConnectionName = "main")
        val registry = MigrationRegistry(
            listOf(
                migrationFactory(::BlankConnectionMigration)
            )
        )
        val migrator = Migrator(repository, resolver, registry)

        val executed = migrator.run(MigrationRunOptions(database = "reports"))

        assertEquals(listOf("BlankConnectionMigration"), executed)
        assertEquals(listOf("BlankConnectionMigration"), repository.getRan("reports"))
        assertTrue(repository.getRan("main").isEmpty())
        assertTrue(
            resolver.connectionHandle("reports").executedStatements.any { statement ->
                statement.contains("blank_connection_entries")
            }
        )
    }

    @Test
    fun `run executes pending migrations on requested database and logs them`() {
        val repository = InMemoryMigrationRepository()
        val resolver = RecordingConnectionResolver(defaultConnectionName = "main")
        val registry = MigrationRegistry(
            listOf(
                migrationFactory(::M2026_04_23_214243_create_terminal_users_table),
                migrationFactory(::M2026_04_23_214311_create_posts_table)
            )
        )
        val migrator = Migrator(repository, resolver, registry)

        val executed = migrator.run(MigrationRunOptions(database = "logs"))

        assertEquals(
            listOf(
                "M2026_04_23_214243_create_terminal_users_table",
                "M2026_04_23_214311_create_posts_table"
            ),
            executed
        )
        assertEquals(
            listOf(
                "M2026_04_23_214243_create_terminal_users_table",
                "M2026_04_23_214311_create_posts_table"
            ),
            repository.getRan("logs")
        )
        assertTrue(repository.getRan("main").isEmpty())
        assertTrue(
            resolver.connectionHandle("logs").executedStatements.any { statement ->
                statement.contains("terminal_users")
            }
        )
        assertTrue(
            resolver.connectionHandle("logs").executedStatements.any { statement ->
                statement.contains("posts")
            }
        )
    }

    @Test
    fun `run honors migration explicit connection over command database`() {
        val repository = InMemoryMigrationRepository()
        val resolver = RecordingConnectionResolver(defaultConnectionName = "main")
        val registry = MigrationRegistry(
            listOf(
                migrationFactory(::LogsOnlyMigration)
            )
        )
        val migrator = Migrator(repository, resolver, registry)

        val executed = migrator.run(MigrationRunOptions(database = "main"))

        assertEquals(listOf("LogsOnlyMigration"), executed)
        assertTrue(resolver.connectionHandle("logs").executedStatements.isNotEmpty())
        assertTrue(resolver.connectionHandle("main").executedStatements.isEmpty())
        assertEquals(listOf("LogsOnlyMigration"), repository.getRan("logs"))
        assertTrue(repository.getRan("main").isEmpty())
    }

    @Test
    fun `run keeps repositories and batch numbers isolated per effective connection`() {
        val repository = InMemoryMigrationRepository().apply {
            setSource("main")
            createRepository()
            log("AlreadyRanOnMain", 3)
            setSource("logs")
            createRepository()
            log("AlreadyRanOnLogs", 7)
        }
        val resolver = RecordingConnectionResolver(defaultConnectionName = "main")
        val registry = MigrationRegistry(
            listOf(
                migrationFactory(::DefaultConnectionMigration),
                migrationFactory(::LogsOnlyMigration),
                migrationFactory(::MariaDbOnlyMigration)
            )
        )
        val migrator = Migrator(repository, resolver, registry)

        val executed = migrator.run()

        assertEquals(
            listOf(
                "DefaultConnectionMigration",
                "LogsOnlyMigration",
                "MariaDbOnlyMigration"
            ),
            executed
        )
        assertEquals(4, repository.getMigrationBatches("main")["DefaultConnectionMigration"])
        assertEquals(8, repository.getMigrationBatches("logs")["LogsOnlyMigration"])
        assertEquals(1, repository.getMigrationBatches("mariadb")["MariaDbOnlyMigration"])
        assertTrue(repository.repositoryExistsOn("main"))
        assertTrue(repository.repositoryExistsOn("logs"))
        assertTrue(repository.repositoryExistsOn("mariadb"))
    }

    @Test
    fun `run validates only option against registry`() {
        val repository = InMemoryMigrationRepository()
        val resolver = RecordingConnectionResolver(defaultConnectionName = "main")
        val registry = MigrationRegistry(
            listOf(
                migrationFactory(::M2026_04_23_214243_create_terminal_users_table)
            )
        )
        val migrator = Migrator(repository, resolver, registry)

        val error = assertFailsWith<IllegalArgumentException> {
            migrator.run(
                MigrationRunOptions(
                    only = setOf("MissingMigration")
                )
            )
        }

        assertTrue(error.message.orEmpty().contains("MissingMigration"))
    }

    @Test
    fun `run returns empty without touching repository when registry is empty`() {
        val repository = object : MigrationRepository {
            override fun getRan(): List<String> = error("No deberia consultar migraciones ejecutadas.")
            override fun getMigrationBatches(): Map<String, Int> = error("No deberia pedir batches.")
            override fun getMigrations(steps: Int): List<MigrationRecord> = error("No deberia pedir rollback.")
            override fun getLast(): List<MigrationRecord> = error("No deberia pedir ultimo batch.")
            override fun log(migration: String, batch: Int) = error("No deberia loggear migraciones.")
            override fun delete(record: MigrationRecord) = error("No deberia borrar migraciones.")
            override fun getNextBatchNumber(): Int = error("No deberia pedir batch.")
            override fun getLastBatchNumber(): Int = error("No deberia pedir ultimo batch.")
            override fun createRepository() = error("No deberia crear repository.")
            override fun repositoryExists(): Boolean = error("No deberia tocar la base.")
            override fun deleteRepository() = error("No deberia borrar repository.")
            override fun setSource(name: String?) = Unit
        }
        val resolver = RecordingConnectionResolver(defaultConnectionName = "main")
        val registry = MigrationRegistry(emptyList())
        val migrator = Migrator(repository, resolver, registry)

        val executed = migrator.run()

        assertTrue(executed.isEmpty())
    }

    @Test
    fun `run uses transactions when the driver supports them and the migration keeps them enabled`() {
        val repository = InMemoryMigrationRepository()
        val resolver = RecordingConnectionResolver(
            defaultConnectionName = "main",
            driver = PostgreSqlDriver
        )
        val registry = MigrationRegistry(
            listOf(
                migrationFactory(::DefaultConnectionMigration)
            )
        )
        val migrator = Migrator(repository, resolver, registry)

        val executed = migrator.run()

        assertEquals(listOf("DefaultConnectionMigration"), executed)
        val handle = resolver.connectionHandle("main")
        assertEquals(1, handle.commitCount)
        assertEquals(0, handle.rollbackCount)
        assertTrue(handle.autoCommit)
    }

    @Test
    fun `run skips transactions when the migration disables them`() {
        val repository = InMemoryMigrationRepository()
        val resolver = RecordingConnectionResolver(
            defaultConnectionName = "main",
            driver = PostgreSqlDriver
        )
        val registry = MigrationRegistry(
            listOf(
                migrationFactory(::WithoutTransactionMigration)
            )
        )
        val migrator = Migrator(repository, resolver, registry)

        val executed = migrator.run()

        assertEquals(listOf("WithoutTransactionMigration"), executed)
        val handle = resolver.connectionHandle("main")
        assertEquals(0, handle.commitCount)
        assertEquals(0, handle.rollbackCount)
        assertTrue(handle.autoCommit)
    }

    @Test
    fun `run rolls back the transaction and avoids logging when a migration execution fails`() {
        val repository = InMemoryMigrationRepository()
        val resolver = RecordingConnectionResolver(
            defaultConnectionName = "main",
            driver = PostgreSqlDriver,
            failOnStatementContainingByConnection = mapOf("main" to "FAIL MIGRATION")
        )
        val registry = MigrationRegistry(
            listOf(
                migrationFactory(::FailingTransactionalMigration)
            )
        )
        val migrator = Migrator(repository, resolver, registry)

        assertFailsWith<IllegalStateException> {
            migrator.run()
        }

        val handle = resolver.connectionHandle("main")
        assertEquals(0, handle.commitCount)
        assertEquals(1, handle.rollbackCount)
        assertTrue(repository.repositoryExistsOn("main"))
        assertTrue(repository.getRan("main").isEmpty())
    }

    @Test
    fun `status returns ran and pending migrations with batch and connection`() {
        val repository = InMemoryMigrationRepository().apply {
            setSource("main")
            createRepository()
            log("M2026_04_23_214243_create_terminal_users_table", 4)
        }
        val resolver = RecordingConnectionResolver(defaultConnectionName = "main")
        val registry = MigrationRegistry(
            listOf(
                migrationFactory(::M2026_04_23_214243_create_terminal_users_table),
                migrationFactory(::LogsOnlyMigration)
            )
        )
        val migrator = Migrator(repository, resolver, registry)

        val statuses = migrator.status(MigrationStatusOptions(database = "main"))

        assertEquals(2, statuses.size)
        assertEquals(
            MigrationStatus(
                migration = "LogsOnlyMigration",
                status = MigrationState.PENDING,
                batch = null,
                connection = "logs"
            ),
            statuses.first()
        )
        assertEquals(
            MigrationStatus(
                migration = "M2026_04_23_214243_create_terminal_users_table",
                status = MigrationState.RAN,
                batch = 4,
                connection = "main"
            ),
            statuses.last()
        )
    }

    @Test
    fun `status reads explicit migration batches from their own connection`() {
        val repository = InMemoryMigrationRepository().apply {
            setSource("logs")
            createRepository()
            log("LogsOnlyMigration", 9)
        }
        val resolver = RecordingConnectionResolver(defaultConnectionName = "main")
        val registry = MigrationRegistry(
            listOf(
                migrationFactory(::M2026_04_23_214243_create_terminal_users_table),
                migrationFactory(::LogsOnlyMigration)
            )
        )
        val migrator = Migrator(repository, resolver, registry)

        val statuses = migrator.status(MigrationStatusOptions(database = "main"))

        assertEquals(
            MigrationStatus(
                migration = "LogsOnlyMigration",
                status = MigrationState.RAN,
                batch = 9,
                connection = "logs"
            ),
            statuses.first()
        )
        assertEquals(MigrationState.PENDING, statuses.last().status)
    }

    @Test
    fun `rollback reverts last batch and removes records`() {
        val repository = InMemoryMigrationRepository().apply {
            setSource("main")
            createRepository()
            log("M2026_04_23_214243_create_terminal_users_table", 1)
            setSource("logs")
            createRepository()
            log("LogsOnlyMigration", 2)
        }
        val resolver = RecordingConnectionResolver(defaultConnectionName = "main")
        val registry = MigrationRegistry(
            listOf(
                migrationFactory(::M2026_04_23_214243_create_terminal_users_table),
                migrationFactory(::LogsOnlyMigration)
            )
        )
        val migrator = Migrator(repository, resolver, registry)

        val rolledBack = migrator.rollback(MigrationRollbackOptions(database = "main", steps = 1))

        assertEquals(listOf("LogsOnlyMigration"), rolledBack)
        assertTrue(resolver.connectionHandle("logs").executedStatements.any { it.contains("log_entries") })
        assertTrue(repository.getRan("logs").isEmpty())
        assertEquals(listOf("M2026_04_23_214243_create_terminal_users_table"), repository.getRan("main"))
    }

    @Test
    fun `rollback with steps walks the latest migrations across connections`() {
        val repository = InMemoryMigrationRepository().apply {
            setSource("main")
            createRepository()
            log("DefaultConnectionMigration", 3)
            setSource("logs")
            createRepository()
            log("LogsOnlyMigration", 4)
            setSource("mariadb")
            createRepository()
            log("MariaDbOnlyMigration", 2)
        }
        val resolver = RecordingConnectionResolver(defaultConnectionName = "main")
        val registry = MigrationRegistry(
            listOf(
                migrationFactory(::DefaultConnectionMigration),
                migrationFactory(::LogsOnlyMigration),
                migrationFactory(::MariaDbOnlyMigration)
            )
        )
        val migrator = Migrator(repository, resolver, registry)

        val rolledBack = migrator.rollback(MigrationRollbackOptions(steps = 2))

        assertEquals(
            listOf(
                "LogsOnlyMigration",
                "DefaultConnectionMigration"
            ),
            rolledBack
        )
        assertTrue(repository.getRan("logs").isEmpty())
        assertTrue(repository.getRan("main").isEmpty())
        assertEquals(listOf("MariaDbOnlyMigration"), repository.getRan("mariadb"))
    }

    @Test
    fun `rollback on a fresh database returns empty without creating repository`() {
        val repository = InMemoryMigrationRepository()
        val resolver = RecordingConnectionResolver(defaultConnectionName = "main")
        val registry = MigrationRegistry(
            listOf(
                migrationFactory(::M2026_04_23_214243_create_terminal_users_table)
            )
        )
        val migrator = Migrator(repository, resolver, registry)

        val rolledBack = migrator.rollback()

        assertTrue(rolledBack.isEmpty())
        assertTrue(repository.getRan("main").isEmpty())
        assertTrue(!repository.repositoryExistsOn("main"))
    }

    @Test
    fun `run executes migrations in ascending order by migration name`() {
        val repository = InMemoryMigrationRepository()
        val resolver = RecordingConnectionResolver(defaultConnectionName = "main")
        val executionOrder = mutableListOf<String>()
        val registry = MigrationRegistry(
            listOf(
                migrationFactory { M2026_04_28_140200_order_third(executionOrder) },
                migrationFactory { M2026_04_28_140000_order_first(executionOrder) },
                migrationFactory { M2026_04_28_140100_order_second(executionOrder) }
            )
        )
        val migrator = Migrator(repository, resolver, registry)

        val executed = migrator.run()

        assertEquals(
            listOf(
                "M2026_04_28_140000_order_first",
                "M2026_04_28_140100_order_second",
                "M2026_04_28_140200_order_third"
            ),
            executed
        )
        assertEquals(executed, executionOrder)
    }

    @Test
    fun `rollback reverts migrations in descending order inside the latest batch`() {
        val repository = InMemoryMigrationRepository().apply {
            setSource("main")
            createRepository()
            log("M2026_04_28_140000_order_first", 7)
            log("M2026_04_28_140100_order_second", 7)
            log("M2026_04_28_140200_order_third", 7)
        }
        val resolver = RecordingConnectionResolver(defaultConnectionName = "main")
        val rollbackOrder = mutableListOf<String>()
        val registry = MigrationRegistry(
            listOf(
                migrationFactory { M2026_04_28_140000_order_first(rollbackOrder) },
                migrationFactory { M2026_04_28_140100_order_second(rollbackOrder) },
                migrationFactory { M2026_04_28_140200_order_third(rollbackOrder) }
            )
        )
        val migrator = Migrator(repository, resolver, registry)

        val rolledBack = migrator.rollback()

        assertEquals(
            listOf(
                "M2026_04_28_140200_order_third",
                "M2026_04_28_140100_order_second",
                "M2026_04_28_140000_order_first"
            ),
            rolledBack
        )
        assertEquals(rolledBack, rollbackOrder)
    }

    @Test
    fun `run uses mariadb grammar and avoids schema transactions when driver does not support them`() {
        val repository = InMemoryMigrationRepository()
        val resolver = RecordingConnectionResolver(
            defaultConnectionName = "main",
            driver = MariaDbDriver
        )
        val registry = MigrationRegistry(
            listOf(
                migrationFactory(::M2026_04_23_214243_create_terminal_users_table)
            )
        )
        val migrator = Migrator(repository, resolver, registry)

        val executed = migrator.run()

        assertEquals(listOf("M2026_04_23_214243_create_terminal_users_table"), executed)
        val handle = resolver.connectionHandle("main")
        assertTrue(handle.executedStatements.any { statement -> statement.contains("CHAR(36)") })
        assertTrue(handle.executedStatements.any { statement -> statement.contains("TIMESTAMP") })
        assertEquals(0, handle.commitCount)
        assertEquals(0, handle.rollbackCount)
    }

    private class InMemoryMigrationRepository : MigrationRepository {
        private val recordsBySource = mutableMapOf<String, MutableMap<Int, MutableList<MigrationRecord>>>()
        val sources = mutableListOf<String?>()
        private var sourceName: String? = null
        private val repositories = mutableMapOf<String, Boolean>()

        override fun getRan(): List<String> {
            return currentRecords().values.flatten()
                .sortedWith(compareBy(MigrationRecord::batch, MigrationRecord::migration))
                .map(MigrationRecord::migration)
        }

        override fun getMigrationBatches(): Map<String, Int> {
            return currentRecords().values.flatten()
                .sortedWith(compareBy(MigrationRecord::batch, MigrationRecord::migration))
                .associate { record -> record.migration to record.batch }
        }

        override fun getMigrations(steps: Int): List<MigrationRecord> {
            require(steps > 0)

            return currentRecords().values.flatten()
                .sortedWith(compareByDescending<MigrationRecord> { it.batch }.thenByDescending { it.migration })
                .take(steps)
        }

        override fun getLast(): List<MigrationRecord> {
            val batch = getLastBatchNumber()
            return currentRecords()[batch]?.sortedByDescending(MigrationRecord::migration).orEmpty()
        }

        override fun log(migration: String, batch: Int) {
            currentRecords().getOrPut(batch, ::mutableListOf) += MigrationRecord(migration, batch)
        }

        override fun delete(record: MigrationRecord) {
            currentRecords()[record.batch]?.remove(record)
        }

        override fun getNextBatchNumber(): Int = getLastBatchNumber() + 1

        override fun getLastBatchNumber(): Int = currentRecords().keys.maxOrNull() ?: 0

        override fun createRepository() {
            repositories[sourceKey()] = true
        }

        override fun repositoryExists(): Boolean = repositories[sourceKey()] ?: false

        override fun deleteRepository() {
            repositories[sourceKey()] = false
            recordsBySource.remove(sourceKey())
        }

        override fun setSource(name: String?) {
            sourceName = name
            sources += sourceName
        }

        fun getRan(name: String?): List<String> {
            val previous = sourceName
            return try {
                sourceName = name
                getRan()
            } finally {
                sourceName = previous
            }
        }

        fun repositoryExistsOn(name: String?): Boolean {
            return repositories[sourceKey(name)] ?: false
        }

        fun getMigrationBatches(name: String?): Map<String, Int> {
            val previous = sourceName
            return try {
                sourceName = name
                getMigrationBatches()
            } finally {
                sourceName = previous
            }
        }

        private fun currentRecords(): MutableMap<Int, MutableList<MigrationRecord>> {
            return recordsBySource.getOrPut(sourceKey()) { mutableMapOf() }
        }

        private fun sourceKey(name: String? = sourceName): String {
            return name ?: "__default__"
        }
    }

    private class RecordingConnectionResolver(
        private val defaultConnectionName: String,
        private val driver: DatabaseDriver = PostgreSqlDriver,
        private val failOnStatementContainingByConnection: Map<String, String> = emptyMap()
    ) : ConnectionResolver {
        private val config = DatabaseConnectionConfig(
            name = "recording",
            driver = driver,
            url = "jdbc:recording"
        )
        private val handles = mutableMapOf<String, RecordingConnectionHandle>()

        override fun connection(name: String?): Connection {
            val target = name ?: defaultConnectionName
            val handle = handles.getOrPut(target) {
                RecordingConnectionHandle(
                    name = target,
                    failOnStatementContaining = failOnStatementContainingByConnection[target]
                )
            }
            return handle.open()
        }

        override fun defaultConnectionName(): String = defaultConnectionName

        override fun connectionConfig(name: String?): DatabaseConnectionConfig = config.copy(
            name = name ?: defaultConnectionName
        )

        fun connectionHandle(name: String): RecordingConnectionHandle {
            return handles.getOrPut(name) { RecordingConnectionHandle(name) }
        }
    }

    private class RecordingConnectionHandle(
        val name: String,
        private val failOnStatementContaining: String? = null
    ) {
        val executedStatements = mutableListOf<String>()
        var autoCommit: Boolean = true
        var commitCount: Int = 0
        var rollbackCount: Int = 0
        var closeCount: Int = 0

        fun open(): Connection {
            val statementProxy = Proxy.newProxyInstance(
                Statement::class.java.classLoader,
                arrayOf(Statement::class.java)
            ) { _, method, args ->
                when (method.name) {
                    "execute" -> {
                        val sql = args?.firstOrNull() as String
                        executedStatements += sql
                        if (failOnStatementContaining != null && failOnStatementContaining in sql) {
                            throw IllegalStateException("Fallo forzado para `$name`: $sql")
                        }
                        true
                    }

                    "close" -> Unit
                    "isClosed" -> false
                    "toString" -> "RecordingStatement($name)"
                    else -> unsupported(method.name)
                }
            } as Statement

            return Proxy.newProxyInstance(
                Connection::class.java.classLoader,
                arrayOf(Connection::class.java)
            ) { _, method, args ->
                when (method.name) {
                    "createStatement" -> statementProxy
                    "getAutoCommit" -> autoCommit
                    "setAutoCommit" -> {
                        autoCommit = args?.firstOrNull() as Boolean
                        Unit
                    }

                    "commit" -> {
                        commitCount++
                        Unit
                    }

                    "rollback" -> {
                        rollbackCount++
                        Unit
                    }

                    "close" -> {
                        closeCount++
                        Unit
                    }

                    "isClosed" -> false
                    "toString" -> "RecordingConnection($name)"
                    else -> unsupported(method.name)
                }
            } as Connection
        }

        private fun unsupported(methodName: String): Nothing {
            throw UnsupportedOperationException("Metodo JDBC no soportado en test: $methodName")
        }
    }

    private class LogsOnlyMigration : Migration() {
        override val connectionName: String = "logs"

        override fun up() {
            create("log_entries") {
                id()
                varchar("message", 255)
            }
        }

        override fun down() {
            dropIfExists("log_entries")
        }
    }

    private class DefaultConnectionMigration : Migration() {
        override fun up() {
            create("default_connection_entries") {
                id()
                varchar("message", 255)
            }
        }

        override fun down() {
            dropIfExists("default_connection_entries")
        }
    }

    private class BlankConnectionMigration : Migration() {
        override val connectionName: String = "   "

        override fun up() {
            create("blank_connection_entries") {
                id()
                varchar("message", 255)
            }
        }

        override fun down() {
            dropIfExists("blank_connection_entries")
        }
    }

    private class MariaDbOnlyMigration : Migration() {
        override val connectionName: String = "mariadb"

        override fun up() {
            create("mariadb_entries") {
                id()
                varchar("message", 255)
            }
        }

        override fun down() {
            dropIfExists("mariadb_entries")
        }
    }

    private class WithoutTransactionMigration : Migration() {
        override val withinTransaction: Boolean = false

        override fun up() {
            create("non_transactional_entries") {
                id()
                varchar("message", 255)
            }
        }

        override fun down() {
            dropIfExists("non_transactional_entries")
        }
    }

    private class FailingTransactionalMigration : Migration() {
        override fun up() {
            statement("FAIL MIGRATION")
        }

        override fun down() {
            statement("ROLLBACK FAIL MIGRATION")
        }
    }

    private class M2026_04_28_140000_order_first(
        private val executionOrder: MutableList<String> = mutableListOf()
    ) : Migration() {
        override fun up() {
            executionOrder += this::class.simpleName.orEmpty()
            create("order_first") {
                id()
            }
        }

        override fun down() {
            executionOrder += this::class.simpleName.orEmpty()
            dropIfExists("order_first")
        }
    }

    private class M2026_04_28_140100_order_second(
        private val executionOrder: MutableList<String> = mutableListOf()
    ) : Migration() {
        override fun up() {
            executionOrder += this::class.simpleName.orEmpty()
            create("order_second") {
                id()
            }
        }

        override fun down() {
            executionOrder += this::class.simpleName.orEmpty()
            dropIfExists("order_second")
        }
    }

    private class M2026_04_28_140200_order_third(
        private val executionOrder: MutableList<String> = mutableListOf()
    ) : Migration() {
        override fun up() {
            executionOrder += this::class.simpleName.orEmpty()
            create("order_third") {
                id()
            }
        }

        override fun down() {
            executionOrder += this::class.simpleName.orEmpty()
            dropIfExists("order_third")
        }
    }
}

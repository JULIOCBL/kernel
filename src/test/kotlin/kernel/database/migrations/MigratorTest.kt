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
import kotlin.test.assertTrue

class MigratorTest {
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

        private fun currentRecords(): MutableMap<Int, MutableList<MigrationRecord>> {
            return recordsBySource.getOrPut(sourceKey()) { mutableMapOf() }
        }

        private fun sourceKey(name: String? = sourceName): String {
            return name ?: "__default__"
        }
    }

    private class RecordingConnectionResolver(
        private val defaultConnectionName: String,
        private val driver: DatabaseDriver = PostgreSqlDriver
    ) : ConnectionResolver {
        private val config = DatabaseConnectionConfig(
            name = "recording",
            driver = driver,
            url = "jdbc:recording"
        )
        private val handles = mutableMapOf<String, RecordingConnectionHandle>()

        override fun connection(name: String?): Connection {
            val target = name ?: defaultConnectionName
            val handle = handles.getOrPut(target) { RecordingConnectionHandle(target) }
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
        val name: String
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
                        executedStatements += args?.firstOrNull() as String
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

    private object RecordingDriver : DatabaseDriver {
        override val id: String = "recording"
        override val defaultJdbcDriverClass: String = "recording.Driver"
        override val supportsSchemaMigrations: Boolean = true
        override val supportsSchemaTransactions: Boolean = true

        override fun buildJdbcUrl(host: String, port: String, database: String): String {
            return "jdbc:recording://$host:$port/$database"
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

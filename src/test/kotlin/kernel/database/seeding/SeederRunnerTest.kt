package kernel.database.seeding

import kernel.concurrency.BlockingTaskRunner
import kernel.database.DB
import kernel.database.orm.Model
import kernel.database.orm.ModelDefinition
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SeederRunnerTest {
    @AfterTest
    fun cleanup() {
        DB.connectionProviderOverride = null
        DB.defaultConnectionNameOverride = null
        RecordingSeeder.events.clear()
    }

    @Test
    fun `database seeder can call nested seeders and returns executed names`() {
        val recorder = SeedConnectionRecorder()
        DB.connectionProviderOverride = { recorder.connection }
        DB.defaultConnectionNameOverride = { "main" }

        val runner = SeederRunner(
            app = kernel.foundation.Application(java.nio.file.Paths.get(".")),
            tasks = ImmediateBlockingTaskRunner(),
            defaultSeeder = RootSeeder::class,
            catalog = setOf(RootSeeder::class, ChildSeeder::class)
        )

        val executed = runner.run()

        assertEquals(listOf("ChildSeeder", "RootSeeder"), executed)
        assertEquals(listOf("child"), RecordingSeeder.events)
        assertEquals(0, recorder.commitCalls)
        assertEquals(0, recorder.closeCalls)
    }

    @Test
    fun `seed runner resolves seeders by simple or qualified name`() {
        val runner = SeederRunner(
            app = kernel.foundation.Application(java.nio.file.Paths.get(".")),
            tasks = ImmediateBlockingTaskRunner(),
            defaultSeeder = RootSeeder::class,
            catalog = setOf(RootSeeder::class, ChildSeeder::class)
        )

        assertEquals(ChildSeeder::class, runner.resolveSeeder("ChildSeeder"))
        assertEquals(ChildSeeder::class, runner.resolveSeeder(ChildSeeder::class.qualifiedName!!))
    }

    @Test
    fun `seeder with explicit connection name runs inside transaction for that connection`() {
        val recorder = SeedConnectionRecorder()
        DB.connectionProviderOverride = { recorder.connection }
        DB.defaultConnectionNameOverride = { "main" }

        val runner = SeederRunner(
            app = kernel.foundation.Application(java.nio.file.Paths.get(".")),
            tasks = ImmediateBlockingTaskRunner(),
            defaultSeeder = TransactionalSeeder::class,
            catalog = setOf(TransactionalSeeder::class)
        )

        val executed = runner.run()

        assertEquals(listOf("TransactionalSeeder"), executed)
        assertEquals(listOf("transactional"), RecordingSeeder.events)
        assertEquals(1, recorder.commitCalls)
        assertEquals(1, recorder.closeCalls)
    }

    @Test
    fun `db table seeder without explicit connection uses default connection on demand`() {
        val recorder = SeedConnectionRecorder()
        DB.connectionProviderOverride = { name ->
            recorder.requestedConnectionNames += name
            recorder.connection
        }
        DB.defaultConnectionNameOverride = { "main" }

        val runner = SeederRunner(
            app = kernel.foundation.Application(java.nio.file.Paths.get(".")),
            tasks = ImmediateBlockingTaskRunner(),
            defaultSeeder = DefaultTableSeeder::class,
            catalog = setOf(DefaultTableSeeder::class)
        )

        val executed = runner.run()

        assertEquals(listOf("DefaultTableSeeder"), executed)
        assertEquals(listOf<String?>("main"), recorder.requestedConnectionNames)
        assertEquals("INSERT INTO lab_users (email, id) VALUES (?, ?)", recorder.lastSql)
        assertEquals(listOf<Any?>("default@example.com", 1), recorder.boundValues)
        assertEquals(0, recorder.commitCalls)
    }

    @Test
    fun `model seeder without explicit connection uses model connection instead of default transaction`() {
        val recorder = SeedConnectionRecorder()
        DB.connectionProviderOverride = { name ->
            recorder.requestedConnectionNames += name
            recorder.connection
        }
        DB.defaultConnectionNameOverride = { "main" }

        val runner = SeederRunner(
            app = kernel.foundation.Application(java.nio.file.Paths.get(".")),
            tasks = ImmediateBlockingTaskRunner(),
            defaultSeeder = ModelConnectionSeeder::class,
            catalog = setOf(ModelConnectionSeeder::class)
        )

        val executed = runner.run()

        assertEquals(listOf("ModelConnectionSeeder"), executed)
        assertEquals(listOf<String?>("logs"), recorder.requestedConnectionNames)
        assertEquals("INSERT INTO audit_users (email, id) VALUES (?, ?)", recorder.lastSql)
        assertEquals(listOf<Any?>("audit@example.com", 9), recorder.boundValues)
        assertEquals(0, recorder.commitCalls)
    }
}

private object RecordingSeeder {
    val events = mutableListOf<String>()
}

private class RootSeeder(
    app: kernel.foundation.Application
) : DatabaseSeeder(app) {
    override suspend fun run() {
        call(ChildSeeder::class)
    }
}

private class ChildSeeder(
    app: kernel.foundation.Application
) : Seeder(app) {
    override suspend fun run() {
        RecordingSeeder.events += "child"
    }
}

private class TransactionalSeeder(
    app: kernel.foundation.Application
) : Seeder(app) {
    override val connectionName: String = "main"

    override suspend fun run() {
        RecordingSeeder.events += "transactional"
    }
}

private class DefaultTableSeeder(
    app: kernel.foundation.Application
) : Seeder(app) {
    override suspend fun run() {
        DB.table("lab_users").insert(
            mapOf(
                "email" to "default@example.com",
                "id" to 1
            )
        )
    }
}

private class ModelConnectionSeeder(
    app: kernel.foundation.Application
) : Seeder(app) {
    override suspend fun run() {
        AuditUser(id = 9, email = "audit@example.com").save()
    }
}

private data class AuditUser(
    val id: Int,
    val email: String
) : Model() {
    override val connectionName: String = "logs"

    override fun primaryKeyValue(): Any? = id

    override fun persistenceAttributes(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "email" to email
        )
    }

    companion object : ModelDefinition<AuditUser>(
        tableName = "audit_users",
        connectionName = "logs",
        mapper = ::unreachableAuditUserMapper
    )
}

private class ImmediateBlockingTaskRunner : BlockingTaskRunner {
    override fun <T> submit(task: () -> T): Future<T> {
        return CompletableFuture.completedFuture(task())
    }
}

private class SeedConnectionRecorder {
    var autoCommit: Boolean = true
    var commitCalls: Int = 0
    var rollbackCalls: Int = 0
    var closeCalls: Int = 0
    var lastSql: String? = null
    val boundValues = mutableListOf<Any?>()
    val requestedConnectionNames = mutableListOf<String?>()

    val connection: Connection = Proxy.newProxyInstance(
        Connection::class.java.classLoader,
        arrayOf(Connection::class.java)
    ) { _, method, args ->
        when (method.name) {
            "prepareStatement" -> preparedStatement(args?.firstOrNull() as String)
            "getAutoCommit" -> autoCommit
            "setAutoCommit" -> {
                autoCommit = args?.firstOrNull() as Boolean
                Unit
            }
            "commit" -> {
                commitCalls += 1
                Unit
            }
            "rollback" -> {
                rollbackCalls += 1
                Unit
            }
            "close" -> {
                closeCalls += 1
                Unit
            }
            "isClosed" -> closeCalls > 0
            "unwrap" -> null
            "isWrapperFor" -> false
            else -> defaultValue(method.returnType)
        }
    } as Connection

    private fun preparedStatement(sql: String): PreparedStatement {
        lastSql = sql
        boundValues.clear()

        return Proxy.newProxyInstance(
            PreparedStatement::class.java.classLoader,
            arrayOf(PreparedStatement::class.java)
        ) { _, method, args ->
            when (method.name) {
                "setObject" -> {
                    val index = (args?.get(0) as Int) - 1
                    while (boundValues.size <= index) {
                        boundValues += null
                    }
                    boundValues[index] = args[1]
                    null
                }

                "executeUpdate" -> 1
                "close" -> null
                "unwrap" -> null
                "isWrapperFor" -> false
                else -> defaultValue(method.returnType)
            }
        } as PreparedStatement
    }

    private fun defaultValue(type: Class<*>): Any? {
        return when (type) {
            java.lang.Boolean.TYPE -> false
            java.lang.Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            java.lang.Double.TYPE -> 0.0
            java.lang.Float.TYPE -> 0f
            java.lang.Short.TYPE -> 0.toShort()
            java.lang.Byte.TYPE -> 0.toByte()
            java.lang.Character.TYPE -> '\u0000'
            Void.TYPE -> Unit
            else -> null
        }
    }
}

private fun unreachableAuditUserMapper(@Suppress("UNUSED_PARAMETER") resultSet: ResultSet): AuditUser {
    error("No deberia llamarse el mapper en esta prueba.")
}

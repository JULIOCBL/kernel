package kernel.database.seeding

import kernel.concurrency.BlockingTaskRunner
import kernel.database.DB
import java.lang.reflect.Proxy
import java.sql.Connection
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
        assertEquals(1, recorder.commitCalls)
        assertEquals(1, recorder.closeCalls)
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

    val connection: Connection = Proxy.newProxyInstance(
        Connection::class.java.classLoader,
        arrayOf(Connection::class.java)
    ) { _, method, args ->
        when (method.name) {
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

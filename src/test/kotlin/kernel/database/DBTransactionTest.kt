package kernel.database

import kotlinx.coroutines.runBlocking
import java.lang.reflect.Proxy
import java.sql.Connection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class DBTransactionTest {
    @Test
    fun `transaction reuses the same connection inside the coroutine context`() = runBlocking {
        val recorder = ConnectionRecorder()
        DB.connectionProviderOverride = { recorder.connection }

        try {
            DB.transaction {
                val first = DB.withConnection { it }
                val second = DB.withConnection { it }

                assertSame(connection, first)
                assertSame(connection, second)
            }

            assertEquals(1, recorder.commitCalls)
            assertEquals(0, recorder.rollbackCalls)
            assertEquals(1, recorder.closeCalls)
        } finally {
            DB.connectionProviderOverride = null
        }
    }

    @Test
    fun `transaction rolls back when the block fails`() = runBlocking {
        val recorder = ConnectionRecorder()
        DB.connectionProviderOverride = { recorder.connection }

        try {
            assertFailsWith<IllegalStateException> {
                DB.transaction {
                    error("boom")
                }
            }

            assertEquals(0, recorder.commitCalls)
            assertEquals(1, recorder.rollbackCalls)
            assertEquals(1, recorder.closeCalls)
        } finally {
            DB.connectionProviderOverride = null
        }
    }

    @Test
    fun `default transaction and explicit default connection share the same context`() = runBlocking {
        val recorder = ConnectionRecorder()
        DB.connectionProviderOverride = { recorder.connection }
        DB.defaultConnectionNameOverride = { "main" }

        try {
            DB.transaction {
                val first = DB.withConnection { it }
                val second = DB.withConnection("main") { it }

                assertSame(connection, first)
                assertSame(connection, second)
            }

            assertEquals(1, recorder.commitCalls)
            assertEquals(0, recorder.rollbackCalls)
            assertEquals(1, recorder.closeCalls)
        } finally {
            DB.connectionProviderOverride = null
            DB.defaultConnectionNameOverride = null
        }
    }
}

private class ConnectionRecorder {
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
            "toString" -> "FakeConnection"
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
